package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.common.config.CacheConfig;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.util.PageUtil;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.dish.config.ViewRateLimiter;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.constant.DishConst;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.dish.dto.HotSearchVO;
import com.bjtufood.dish.dto.RatingDistributionVO;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.history.service.HistoryService;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.entity.ReviewUseful;
import com.bjtufood.review.mapper.ReviewMapper;
import com.bjtufood.review.mapper.ReviewUsefulMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DishServiceImpl implements DishService {

    /** 搜索别名最大长度（与 schema.sql dish.alias VARCHAR(255) 对齐，含逗号分隔符） */
    private static final int ALIAS_MAX_LENGTH = 255;

    private final DishMapper dishMapper;
    private final StallMapper stallMapper;
    private final ReviewMapper reviewMapper;
    private final ReviewUsefulMapper reviewUsefulMapper;
    private final HistoryService historyService;
    private final ImageUrlUtil imageUrlUtil;
    private final ViewRateLimiter viewRateLimiter;

    @Override
    public IPage<DishVO> listDishes(DishQueryReq req) {
        if (req == null) {
            req = new DishQueryReq();
        }
        // 统一走 PageUtil.normalize（null 先兜底为 0 交由工具类归一化），与其他分页入口保持一致
        int[] norm = PageUtil.normalize(
                req.getPage() == null ? 0 : req.getPage(),
                req.getPageSize() == null ? 0 : req.getPageSize());
        req.setPage(norm[0]);
        req.setPageSize(norm[1]);
        return dishMapper.selectDishPage(new Page<>(req.getPage(), req.getPageSize()), req)
                .convert(this::enrichImages);
    }

    @Override
    public List<DishVO> getHotDishes() {
        return getHotDishes(null, null, null);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_DISH_HOT,
            key = "'hot:' + (#lat ?: 'null') + ':' + (#lng ?: 'null') + ':' + (#limit ?: 'default')")
    public List<DishVO> getHotDishes(java.math.BigDecimal lat, java.math.BigDecimal lng, Integer limit) {
        boolean byDistance = lat != null && lng != null;
        List<com.bjtufood.dish.dto.DishVO> list = byDistance
                ? dishMapper.selectHotDishesByDistance(lat, lng)
                : dishMapper.selectHotDishes();
        List<DishVO> result = list.stream()
                .map(this::enrichImages)
                .toList();
        if (limit != null && limit > 0 && result.size() > limit) {
            result = result.subList(0, limit);
        }
        return result;
    }

    @Override
    public DishVO getDishDetail(Long id, Long userId) {
        DishDetailVO vo = dishMapper.selectDishDetail(id);
        if (vo == null) {
            throw new BusinessException("菜品不存在");
        }

        // 从 images_json 解析 images（避免二次查数据库）
        enrichImages(vo);

        // 查询评分分布
        List<RatingDistributionVO> distribution = dishMapper.selectRatingDistribution(id);
        vo.setRatingDistribution(fillRatingDistribution(distribution));

        // 当前用户状态（收藏/favorite 模块已整体移除；仅保留是否已评价）
        if (userId != null) {
            vo.setHasReviewed(reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                    .eq(Review::getUserId, userId)
                    .eq(Review::getDishId, id)) > 0);
        }
        return vo;
    }

    @Override
    public List<DishVO> getNewDishes() {
        return dishMapper.selectNewDishes()
                .stream()
                .map(this::enrichImages)
                .toList();
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_DISH_RECOMMEND,
            key = "'rec:' + #page + ':' + #pageSize + ':' + (#excludeIds ?: '') + ':' + (#userId ?: 0)")
    public IPage<DishVO> recommendDishes(int page, int pageSize, String excludeIds, Long userId) {
        // 统一走 PageUtil.normalize（page<1→1，pageSize<1→10，pageSize>100→100），与其他分页入口保持一致
        int[] norm = PageUtil.normalize(page, pageSize);
        page = norm[0];
        pageSize = norm[1];
        List<Long> exclude = parseExcludeIds(excludeIds);

        // 个性化路径：有浏览足迹的登录用户，对其足迹同类（同 stall / 同 tags）菜品加权。
        // 无足迹/未登录直接走 DB 侧分页 + 热度排序（excludeIds 下推），避免全表 selectList 后内存排序（M2 优化）。
        if (userId != null) {
            List<Long> recentDishIds = historyService.recentViewedDishIds(userId, 20);
            if (!recentDishIds.isEmpty()) {
                java.util.Set<Long> recentStallIds = new java.util.HashSet<>();
                java.util.Set<String> recentTags = new java.util.HashSet<>();
                dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                                .in(Dish::getId, recentDishIds))
                        .forEach(r -> {
                            if (r.getStallId() != null) recentStallIds.add(r.getStallId());
                            if (StringUtils.hasText(r.getTags())) {
                                java.util.Arrays.stream(r.getTags().split(","))
                                        .map(String::trim).filter(t -> !t.isEmpty())
                                        .forEach(recentTags::add);
                            }
                        });
                if (!recentStallIds.isEmpty() || !recentTags.isEmpty()) {
                    return recommendWithPersonalization(page, pageSize, exclude,
                            recentDishIds.size(), recentStallIds, recentTags);
                }
            }
        }

        // 常规路径：DB 侧分页 + 热度排序（未登录/无足迹，推荐降级纯热度）
        DishQueryReq req = new DishQueryReq();
        req.setExcludeIds(exclude);
        req.setSortBy("heat");
        req.setSortOrder("desc");
        return dishMapper.selectDishPage(new Page<>(page, pageSize), req)
                .convert(this::enrichImages);
    }

    /**
     * 解析推荐接口 excludeIds（逗号分隔数字串）为 id 集合，空串返回空集合。
     */
    private List<Long> parseExcludeIds(String excludeIds) {
        if (!StringUtils.hasText(excludeIds)) {
            return List.of();
        }
        // M3 修复：超长纯数字串（>18 位）Long.valueOf 会抛 NumberFormatException → 500。
        // 逐项安全解析，溢出的项直接跳过（游客可公开访问 /dishes/recommend，须防低成本 500）。
        List<Long> result = new java.util.ArrayList<>();
        for (String s : excludeIds.split(",")) {
            String t = s.trim();
            if (t.isEmpty() || t.length() > 18 || !t.matches("\\d+")) {
                continue;
            }
            try {
                result.add(Long.parseLong(t));
            } catch (NumberFormatException ignore) {
                // 溢出项忽略，不影响其余合法 id
            }
        }
        return result.stream().distinct().toList();
    }

    /**
     * 个性化推荐路径：基于浏览足迹对同档口/同标签菜品加权，内存排序后分页。
     * 仅命中足迹的登录用户触发（候选集通常远小于全表，且此类用户占比低，全量加载可接受）。
     */
    private IPage<DishVO> recommendWithPersonalization(int page, int pageSize, List<Long> exclude,
                                                       int recentCount,
                                                       java.util.Set<Long> recentStallIds,
                                                       java.util.Set<String> recentTags) {
        // 仅 approved 且上架菜品参与推荐
        List<Dish> candidates = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getAuditStatus, DishConst.AUDIT_APPROVED)
                .eq(Dish::getStatus, DishConst.STATUS_ON));
        // 排除前端已展示项
        if (!exclude.isEmpty()) {
            candidates = candidates.stream()
                    .filter(d -> !exclude.contains(d.getId()))
                    .toList();
        }
        // 热度分：w1*viewCount + w2*ratingCount*scale + w3*avgRating*scale
        // 权重常量：w1=1, w2=5, w3=20（见 spec §3.x.4）
        final int w1 = 1, w2 = 5, w3 = 20;
        // 平滑个性化加权：命中足迹越多权重越大，但设上限避免个例压倒热度分（原魔数 500.0 会让单条足迹直接封顶）
        final double bonus = Math.min(recentCount * 50.0, 300.0);

        candidates.sort((a, b) -> Double.compare(
                personalizedHeat(b, w1, w2, w3, bonus, recentStallIds, recentTags),
                personalizedHeat(a, w1, w2, w3, bonus, recentStallIds, recentTags)));

        long total = candidates.size();
        int from = Math.min((page - 1) * pageSize, candidates.size());
        int to = Math.min(from + pageSize, candidates.size());
        List<Dish> pageSlice = candidates.subList(from, to);
        // 消除逐条 selectDishDetail 的 N+1：整页 id 批量 IN 查一次，再按 id 组装
        Map<Long, DishDetailVO> detailMap = loadDishDetailsByIds(
                pageSlice.stream().map(Dish::getId).distinct().toList());
        List<DishVO> records = pageSlice.stream()
                .map(d -> {
                    DishDetailVO vo = detailMap.get(d.getId());
                    return vo != null ? enrichImages(vo) : null;
                })
                .filter(Objects::nonNull)
                .toList();
        IPage<DishVO> result = new Page<>(page, pageSize, total);
        result.setRecords(records);
        return result;
    }

    /**
     * 批量查询菜品详情（IN 查询建 Map），消除推荐列表逐条详情的 N+1。
     * 空集合防护：避免生成非法 SQL "IN ()"。
     */
    private Map<Long, DishDetailVO> loadDishDetailsByIds(List<Long> ids) {
        Map<Long, DishDetailVO> map = new java.util.HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        dishMapper.selectDishDetailsByIds(ids)
                .forEach(vo -> map.put(vo.getId(), vo));
        return map;
    }

    private double personalizedHeat(Dish d, int w1, int w2, int w3, double bonus,
                                    java.util.Set<Long> stallIds, java.util.Set<String> tags) {
        double h = heat(d, w1, w2, w3);
        if (bonus <= 0) return h;
        double extra = 0;
        if (d.getStallId() != null && stallIds.contains(d.getStallId())) extra += bonus;
        if (StringUtils.hasText(d.getTags())) {
            long hit = java.util.Arrays.stream(d.getTags().split(","))
                    .map(String::trim).filter(tags::contains).count();
            extra += hit * bonus * 0.5;
        }
        return h + extra;
    }

    private double heat(Dish d, int w1, int w2, int w3) {
        int view = d.getViewCount() == null ? 0 : d.getViewCount();
        int ratingCount = d.getRatingCount() == null ? 0 : d.getRatingCount();
        double avg = d.getAvgRating() == null ? 0 : d.getAvgRating().doubleValue();
        return w1 * view + w2 * ratingCount * 20 + w3 * avg;
    }

    @Override
    public List<DishVO> getPromotionDishes() {
        return dishMapper.selectPromotionDishes()
                .stream()
                .map(this::enrichImages)
                .toList();
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_DISH_HOT_SEARCH, key = "'all'")
    public List<HotSearchVO> hotSearch() {
        return dishMapper.selectHotSearch();
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_DISH_RISING, key = "'all'")
    public List<DishVO> rising() {
        return dishMapper.selectRising().stream()
                .map(this::enrichImages)
                .toList();
    }

    @Override
    public void addViewCount(Long dishId, Long userId) {
        // 防刷（P1-5）：同一用户对同一菜品 5 分钟窗口内只计 1 次（内存去重，窗口内重复直接忽略）
        if (!viewRateLimiter.tryAcquire(userId, dishId)) {
            return;
        }
        // 并发安全：原子自增（UPDATE ... SET view_count = view_count + 1），避免读-改-写丢计数
        int affected = dishMapper.increaseViewCount(dishId);
        if (affected == 0) {
            throw new BusinessException("菜品不存在");
        }
        // 记录浏览足迹（去重），供「猜你喜欢」个性化读取；游客不记录（recordDishView 内部判空）
        historyService.recordDishView(userId, dishId);
    }

    @Override
    public IPage<DishAdminVO> listAllForAdmin(int page, int pageSize) {
        // 分页上限统一由 PageUtil 约束，避免一次性全表加载
        int[] norm = PageUtil.normalize(page, pageSize);
        page = norm[0];
        pageSize = norm[1];
        IPage<DishAdminVO> result = dishMapper.selectAllForAdmin(new Page<>(page, pageSize));
        result.setRecords(result.getRecords().stream()
                .map(this::enrichDishAdminImages)
                .toList());
        return result;
    }

    @Override
    @CacheEvict(cacheNames = {CacheConfig.CACHE_DISH_HOT, CacheConfig.CACHE_DISH_RECOMMEND,
        CacheConfig.CACHE_DISH_HOT_SEARCH, CacheConfig.CACHE_DISH_RISING}, allEntries = true)
    public void addDish(DishAdminReq req) {
        // 新增必填校验（DTO 层已放开以支持部分更新，必填在此兜底）
        if (!StringUtils.hasText(req.getName())) {
            throw new BusinessException("菜品名称不能为空");
        }
        if (req.getPrice() == null) {
            throw new BusinessException("价格不能为空");
        }
        // 产品定型：菜品首图必填（无图不录入 / 不上架；已上架的老数据不受影响）
        if (req.getImages() == null || req.getImages().isEmpty()) {
            throw new BusinessException("请至少上传 1 张菜品图");
        }
        // 校验 stallId 对应的档口是否存在
        if (req.getStallId() == null || stallMapper.selectById(req.getStallId()) == null) {
            throw new BusinessException("档口不存在");
        }
        Dish dish = new Dish();
        applyReq(dish, req);
        dish.setAvgRating(BigDecimal.ZERO);
        dish.setRatingCount(0);
        dish.setViewCount(0);
        if (!StringUtils.hasText(dish.getStatus())) {
            dish.setStatus(DishConst.STATUS_ON);
        }
        // 产品定型（2026-09-14 用户拍板）：管理员即权威——后台新增直接审核通过。
        // 否则落库默认 audit_status='pending'，而小程序端仅展示 approved，新录入的菜品将全部不可见。
        dish.setAuditStatus(DishConst.AUDIT_APPROVED);
        dishMapper.insert(dish);
    }

    @Override
    @CacheEvict(cacheNames = {CacheConfig.CACHE_DISH_HOT, CacheConfig.CACHE_DISH_RECOMMEND,
        CacheConfig.CACHE_DISH_HOT_SEARCH, CacheConfig.CACHE_DISH_RISING}, allEntries = true)
    public void updateDish(Long id, DishAdminReq req) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        // 首图不可清空：显式传入空 images 视为清空，拒绝（未传 images 的部分更新不校验）
        if (req.getImages() != null && req.getImages().isEmpty()) {
            throw new BusinessException("请至少保留 1 张菜品图");
        }
        applyReq(dish, req);
        // 同上（2026-09-14 用户拍板）：管理员编辑视为权威操作，确保菜品保持可见，
        // 顺带修正历史 pending/rejected 态，避免"改了信息反而从端上消失"。
        dish.setAuditStatus(DishConst.AUDIT_APPROVED);
        dishMapper.updateById(dish);
        // 契约约定：null 表示清空可空的原价/促销价；updateById 默认 NOT_NULL 策略不落 null，需显式置空
        boolean clearOriginalPrice = req.getOriginalPrice() == null;
        boolean clearPromoPrice = req.getPromoPrice() == null;
        // alias 契约与原价/促销价不同：null=不修改（保护「仅传 status 的行内部分更新」不误清别名）；
        // 传了字段（含空串/纯空白）但规范化后为空 = 清空别名，updateById 不落 null，需显式置空。
        boolean clearAlias = req.getAlias() != null
                && !StringUtils.hasText(normalizeAlias(req.getAlias()));
        if (clearOriginalPrice || clearPromoPrice || clearAlias) {
            LambdaUpdateWrapper<Dish> clearWrapper = new LambdaUpdateWrapper<Dish>().eq(Dish::getId, id);
            if (clearOriginalPrice) {
                clearWrapper.set(Dish::getOriginalPrice, null);
            }
            if (clearPromoPrice) {
                clearWrapper.set(Dish::getPromoPrice, null);
            }
            if (clearAlias) {
                clearWrapper.set(Dish::getAlias, null);
            }
            dishMapper.update(null, clearWrapper);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = {CacheConfig.CACHE_DISH_HOT, CacheConfig.CACHE_DISH_RECOMMEND,
        CacheConfig.CACHE_DISH_HOT_SEARCH, CacheConfig.CACHE_DISH_RISING}, allEntries = true)
    public void deleteDish(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        // 级联清理评价与「有用」标记（BE-108）：先删引用 review.id 的 review_useful，再删评价本体
        reviewUsefulMapper.delete(new LambdaQueryWrapper<ReviewUseful>()
                .inSql(ReviewUseful::getReviewId, "SELECT id FROM review WHERE dish_id = " + id));
        reviewMapper.delete(new LambdaQueryWrapper<Review>().eq(Review::getDishId, id));
        dishMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // 评分重算由 RatingUpdateListener 在事务 AFTER_COMMIT 后异步触发，
    // evict 发生在本方法写库完成之后（@CacheEvict 默认 afterInvocation），
    // 不会出现「先清缓存、后写库」导致旧评分被回填的窗口
    @CacheEvict(cacheNames = {CacheConfig.CACHE_DISH_HOT, CacheConfig.CACHE_DISH_RECOMMEND,
        CacheConfig.CACHE_DISH_HOT_SEARCH, CacheConfig.CACHE_DISH_RISING}, allEntries = true)
    public void recalcAvgRating(Long dishId) {
        // 并发安全：子查询 AVG/COUNT 整体写回（仅统计未隐藏评价），避免全量查询后回写丢数据
        dishMapper.recalcRatingBySubquery(dishId);
    }

    // syncCollectCount 已随 favorite 模块移除（task-12.12）；喜欢计数存储方案待架构师评估。

    private void applyReq(Dish dish, DishAdminReq req) {
        dish.setStallId(req.getStallId());
        dish.setCategoryId(req.getCategoryId());
        dish.setName(req.getName());
        String alias = normalizeAlias(req.getAlias());
        if (alias != null && alias.length() > ALIAS_MAX_LENGTH) {
            throw new BusinessException("搜索别名过长（含逗号分隔符最多 " + ALIAS_MAX_LENGTH + " 字符）");
        }
        dish.setAlias(alias);
        dish.setPrice(req.getPrice());
        dish.setOriginalPrice(req.getOriginalPrice());
        dish.setPromoPrice(req.getPromoPrice());
        dish.setDescription(req.getDescription());
        dish.setImages(JsonListUtil.toJson(req.getImages()));
        dish.setTags(req.getTags());
        dish.setStatus(req.getStatus());
    }

    /**
     * 搜索别名规范化：支持中英文逗号分隔，逐项 trim、去空项、去重（保持首次出现顺序）。
     *
     * @param raw 原始输入（可空）
     * @return null=未传（不修改）；空串=清空；非空=逗号分隔的规范化别名
     */
    private static String normalizeAlias(String raw) {
        if (raw == null) {
            return null;
        }
        java.util.LinkedHashSet<String> parts = new java.util.LinkedHashSet<>();
        for (String part : raw.split("[,，]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts.isEmpty() ? "" : String.join(",", parts);
    }

    /**
     * 将数据库原始的 imagesJson 解析为 List{@literal <String>} 并回填到 images
     */
    private DishVO enrichImages(DishVO vo) {
        if (vo == null) {
            return null;
        }
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(vo.getImagesJson()));
        return vo;
    }

    /**
     * 对后台菜品 VO 的 imagesJson 字段进行 URL 转换
     */
    private DishAdminVO enrichDishAdminImages(DishAdminVO vo) {
        if (vo == null) {
            return null;
        }
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(vo.getImagesJson()));
        return vo;
    }

    /**
     * 补齐 1-5 星评分分布，缺失的星级补 0
     */
    private List<RatingDistributionVO> fillRatingDistribution(List<RatingDistributionVO> distribution) {
        List<RatingDistributionVO> result = new ArrayList<>();
        for (int star = 5; star >= 1; star--) {
            long count = 0;
            for (RatingDistributionVO rd : distribution) {
                if (Objects.equals(rd.getStar(), star)) {
                    count = rd.getCount() == null ? 0 : rd.getCount();
                    break;
                }
            }
            result.add(new RatingDistributionVO(star, count));
        }
        return result;
    }
}
