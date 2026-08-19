package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.util.PageUtil;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishPublishReq;
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
import com.bjtufood.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
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

    private final DishMapper dishMapper;
    private final StallMapper stallMapper;
    private final ReviewMapper reviewMapper;
    private final HistoryService historyService;
    private final ImageUrlUtil imageUrlUtil;

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
    public List<HotSearchVO> hotSearch() {
        return dishMapper.selectHotSearch();
    }

    @Override
    public List<DishVO> rising() {
        return dishMapper.selectRising().stream()
                .map(this::enrichImages)
                .toList();
    }

    @Override
    public void addViewCount(Long dishId, Long userId) {
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
    public void addDish(DishAdminReq req) {
        // 新增必填校验（DTO 层已放开以支持部分更新，必填在此兜底）
        if (!StringUtils.hasText(req.getName())) {
            throw new BusinessException("菜品名称不能为空");
        }
        if (req.getPrice() == null) {
            throw new BusinessException("价格不能为空");
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
        dishMapper.insert(dish);
    }

    @Override
    public void updateDish(Long id, DishAdminReq req) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        applyReq(dish, req);
        dishMapper.updateById(dish);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDish(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        reviewMapper.delete(new LambdaQueryWrapper<Review>().eq(Review::getDishId, id));
        dishMapper.deleteById(id);
    }

    // ==================== 学生端发布接口实现 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStudentDish(DishPublishReq req, Long userId) {
        if (req.getStallId() == null || stallMapper.selectById(req.getStallId()) == null) {
            throw new BusinessException("档口不存在");
        }
        Dish dish = new Dish();
        applyPublishReq(dish, req);
        dish.setCreatedBy(userId);
        // 审核状态机：学生提交即 pending；状态与审核解耦，默认上架待审核通过后展示
        dish.setAuditStatus(DishConst.AUDIT_PENDING);
        dish.setRejectReason(null);
        dish.setStatus(DishConst.STATUS_ON);
        dish.setAvgRating(BigDecimal.ZERO);
        dish.setRatingCount(0);
        dish.setViewCount(0);
        dishMapper.insert(dish);
        return dish.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStudentDish(Long id, DishPublishReq req, Long userId) {
        Dish existing = dishMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("菜品不存在");
        }
        if (!userId.equals(existing.getCreatedBy())) {
            throw new BusinessException("只能编辑自己发布的菜品");
        }
        applyPublishReq(existing, req);
        // 编辑重提：复用原记录，审核状态回到 pending，退回原因清空
        existing.setAuditStatus(DishConst.AUDIT_PENDING);
        existing.setRejectReason(null);
        dishMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMyDish(Long id, Long userId) {
        Dish existing = dishMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("菜品不存在");
        }
        if (!userId.equals(existing.getCreatedBy())) {
            throw new BusinessException(403, "只能删除自己发布的菜品");
        }
        // 复用现有管理员删除的级联清理逻辑（评价；favorite/清单 模块已移除）
        reviewMapper.delete(new LambdaQueryWrapper<Review>().eq(Review::getDishId, id));
        dishMapper.deleteById(id);
    }

    private void applyPublishReq(Dish dish, DishPublishReq req) {
        dish.setStallId(req.getStallId());
        dish.setName(req.getName());
        dish.setPrice(req.getPrice());
        dish.setDescription(req.getDescription());
        dish.setImages(JsonListUtil.toJson(req.getImages()));
        dish.setTags(req.getTags());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalcAvgRating(Long dishId) {
        // 并发安全：子查询 AVG/COUNT 整体写回（仅统计未隐藏评价），避免全量查询后回写丢数据
        dishMapper.recalcRatingBySubquery(dishId);
    }

    // syncCollectCount 已随 favorite 模块移除（task-12.12）；喜欢计数存储方案待架构师评估。

    private void applyReq(Dish dish, DishAdminReq req) {
        dish.setStallId(req.getStallId());
        dish.setCategoryId(req.getCategoryId());
        dish.setName(req.getName());
        dish.setPrice(req.getPrice());
        dish.setOriginalPrice(req.getOriginalPrice());
        dish.setPromoPrice(req.getPromoPrice());
        dish.setDescription(req.getDescription());
        dish.setImages(JsonListUtil.toJson(req.getImages()));
        dish.setTags(req.getTags());
        dish.setStatus(req.getStatus());
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
