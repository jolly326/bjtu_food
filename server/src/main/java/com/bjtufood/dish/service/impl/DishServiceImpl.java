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
import com.bjtufood.history.entity.ViewLog;
import com.bjtufood.history.mapper.ViewLogMapper;
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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DishServiceImpl implements DishService {

    /** 搜索别名最大长度（与 schema.sql dish.alias VARCHAR(255) 对齐，含逗号分隔符） */
    private static final int ALIAS_MAX_LENGTH = 255;

    /** view_log.target_type 值：菜品（与 ViewLog 实体注释 / HistoryServiceImpl 写入口径一致） */
    private static final String VIEW_TARGET_TYPE_DISH = "dish";

    private final DishMapper dishMapper;
    private final StallMapper stallMapper;
    private final ReviewMapper reviewMapper;
    private final ReviewUsefulMapper reviewUsefulMapper;
    private final ViewLogMapper viewLogMapper;
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
    @Cacheable(cacheNames = CacheConfig.CACHE_DISH_HOT_SEARCH, key = "'all'")
    public List<HotSearchVO> hotSearch() {
        return dishMapper.selectHotSearch();
    }

    /**
     * 浏览量上报（2026-09-14 §7.14 A：同一用户对同一菜品每天只计 1 次）。
     * <p>
     * 去重真源为 view_log 表（user_id + target_type='dish' + target_id 的 created_at 落在
     * 「当天」，自然日按 Asia/Shanghai 切分），与原 5 分钟内存窗口
     * （{@link ViewRateLimiter}，多用于吸收短时间内的刷新抖动）叠加生效：
     * <ol>
     *   <li>5 分钟窗口命中 → 立即返回（省去一次 DB 查询）；</li>
     *   <li>当日已存在浏览记录 → 幂等返回成功：<b>不自增 view_count，也不重复插记录</b>；</li>
     *   <li>否则插入一条 view_log 并执行原子自增，同时刷新浏览足迹时间（供「猜你喜欢」）。</li>
     * </ol>
     * 并发说明（已登记、不修）：第 2 步「先查后插」存在极小竞态窗口——两个并发首次请求可能
     * 同时查不到当日记录，导致当日最多多计 1 次（insert 无唯一键约束，view_log 表结构不变）。
     * 该偏差对热度排序无实质影响，故按用户拍板不加唯一键。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addViewCount(Long dishId, Long userId) {
        // 防刷（P1-5）：同一用户对同一菜品 5 分钟窗口内只计 1 次（内存去重，窗口内重复直接忽略）
        if (!viewRateLimiter.tryAcquire(userId, dishId)) {
            return;
        }
        // 当日去重（§7.14 A）：当天已计过则幂等返回，不自增、不重复插记录
        if (historyService.existsTodayDishView(userId, dishId)) {
            return;
        }
        // 记录浏览足迹（去重），供「猜你喜欢」个性化读取；游客不记录（recordDishView 内部判空）
        historyService.recordDishView(userId, dishId);
        // 并发安全：原子自增（UPDATE ... SET view_count = view_count + 1），避免读-改-写丢计数
        int affected = dishMapper.increaseViewCount(dishId);
        if (affected == 0) {
            throw new BusinessException("菜品不存在");
        }
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
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = {CacheConfig.CACHE_DISH_HOT_SEARCH}, allEntries = true)
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
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = {CacheConfig.CACHE_DISH_HOT_SEARCH}, allEntries = true)
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
        // 契约约定：null/0 表示清空可空的原价/促销价（applyReq 已把 0 归一为 null 并写回实体）；
        // updateById 默认 NOT_NULL 策略不落 null，需显式置空
        boolean clearOriginalPrice = dish.getOriginalPrice() == null;
        boolean clearPromoPrice = dish.getPromoPrice() == null;
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
    @CacheEvict(cacheNames = {CacheConfig.CACHE_DISH_HOT_SEARCH}, allEntries = true)
    public void deleteDish(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        // 级联清理评价与「有用」标记（BE-108）：先删引用 review.id 的 review_useful，再删评价本体
        reviewUsefulMapper.delete(new LambdaQueryWrapper<ReviewUseful>()
                .inSql(ReviewUseful::getReviewId, "SELECT id FROM review WHERE dish_id = " + id));
        reviewMapper.delete(new LambdaQueryWrapper<Review>().eq(Review::getDishId, id));
        // 级联清理浏览足迹（P2-04）：target_type='dish' + target_id 的 view_log 行，
        // 否则菜品物理删除后残留孤儿行（且「猜你喜欢」按已删菜品 ID 读取恒空）。与评价级联同属本事务。
        viewLogMapper.delete(new LambdaQueryWrapper<ViewLog>()
                .eq(ViewLog::getTargetType, VIEW_TARGET_TYPE_DISH)
                .eq(ViewLog::getTargetId, id));
        dishMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // 评分重算由 RatingUpdateListener 在事务 AFTER_COMMIT 后异步触发，
    // evict 发生在本方法写库完成之后（@CacheEvict 默认 afterInvocation），
    // 不会出现「先清缓存、后写库」导致旧评分被回填的窗口
    @CacheEvict(cacheNames = {CacheConfig.CACHE_DISH_HOT_SEARCH}, allEntries = true)
    public void recalcAvgRating(Long dishId) {
        // 并发安全：子查询 AVG/COUNT 整体写回，避免全量查询后回写丢数据。
        // 计入口径（Q-110，2026-09-14 用户拍板）：仅 is_hidden=0 且 sec_state='pass' 的评价计入，
        // 被机审/人工拦下（review/rejected）的内容完全不进统计；口径真源在 DishMapper.xml
        // recalcRatingBySubquery。全量重算与增量路径（新增/删除/隐藏/机审回写）统一走本方法。
        dishMapper.recalcRatingBySubquery(dishId);
    }

    /**
     * 请求体 → 实体写入（新增与编辑共用）。
     * <p>
     * 可选字段（price/originalPrice/promoPrice/description/tags/spiceLevel/region/status）
     * 遵循「null=不修改」语义：编辑路径 MyBatis-Plus {@code updateById} 默认 NOT_NULL 策略会跳过 null 字段，
     * 新增路径 null 则落库列默认值（与既有 applyReq 风格一致）。
     * <p>
     * 所有值域/白名单校验集中在此（PR-06：非法入参必须 400 报错，不得静默降级落库）。
     */
    private void applyReq(Dish dish, DishAdminReq req) {
        dish.setStallId(req.getStallId());
        dish.setCategoryId(req.getCategoryId());
        dish.setName(req.getName());
        String alias = normalizeAlias(req.getAlias());
        if (alias != null && alias.length() > ALIAS_MAX_LENGTH) {
            throw new BusinessException("搜索别名过长（含逗号分隔符最多 " + ALIAS_MAX_LENGTH + " 字符）");
        }
        dish.setAlias(alias);

        // 金额值域校验与归一化（P1-05）：单位仍为「分」，仅加值域约束，不改量纲。
        // price 为必填价格：非 null 时必须 > 0，0/负数 → 400。
        // originalPrice/promoPrice 为可空折扣字段：web 以 0 表示「无原价/无折扣」（schema 用 NULL 表达该语义），
        // 故 0 归一为 null（清空/未设置），负数 → 400；真正设值时必须 > 0 且 promoPrice <= originalPrice。
        Integer price = req.getPrice();
        if (price != null && price <= 0) {
            throw new BusinessException("价格必须大于 0（单位：分）");
        }
        Integer originalPrice = normalizeDiscount(req.getOriginalPrice(), "原价");
        Integer promoPrice = normalizeDiscount(req.getPromoPrice(), "促销价");
        validateDiscountRelation(originalPrice, promoPrice);
        dish.setPrice(price);
        dish.setOriginalPrice(originalPrice);
        dish.setPromoPrice(promoPrice);

        dish.setDescription(req.getDescription());
        dish.setImages(JsonListUtil.toJson(req.getImages()));
        // 标签白名单校验（P2-01）：schema 注释声明「仅允许登记值」，非法值 400
        dish.setTags(validateAndNormalizeTags(req.getTags()));

        // 辣度（P0-01）：原漏写导致字段恒为默认值，首页辣度筛选恒空集；此处补齐写入
        // 注：分量 portion 已于 2026-09-14 §7.14（Q-114）整体下线，连同写入/校验一并移除。
        validateSpiceLevel(req.getSpiceLevel());
        dish.setSpiceLevel(req.getSpiceLevel());

        // 风味/菜系（§7.9 定型）：先前后台无维护入口，此处补齐写入
        dish.setRegion(req.getRegion());
        dish.setStatus(req.getStatus());
    }

    /**
     * 折扣字段（原价/促销价，单位：分）归一化与值域校验（P1-05）。
     * <p>
     * 语义（与 web 管理端既有契约对齐）：null / 0 均表示「无该价格」→ 落 NULL；
     * 负数非法 → 400；正数原样保留（schema 中 NULL 表示无折扣，0 虽为金额但语义上等于「无」，
     * 归一为 NULL 可避免「库中存在 0 元原价」这一无意义状态）。
     */
    private Integer normalizeDiscount(Integer value, String fieldName) {
        if (value == null || value == 0) {
            return null;
        }
        if (value < 0) {
            throw new BusinessException(fieldName + "不能为负（单位：分）");
        }
        return value;
    }

    /**
     * 折扣组合校验：promoPrice 非空（真正设了促销价）时不得高于 originalPrice（原价非空时比较）。
     * 原价为空（无折扣基准）时不做比较。
     */
    private void validateDiscountRelation(Integer originalPrice, Integer promoPrice) {
        if (originalPrice != null && promoPrice != null && promoPrice > originalPrice) {
            throw new BusinessException("促销价不能高于原价");
        }
    }

    /**
     * 标签白名单校验与规范化：支持中英文逗号分隔，逐项 trim、去空项、去重（保持首次出现顺序）。
     * 空白输入返回 null（清空）；任一项不在 {@link DishConst#VALID_TAGS} 内 → 400（PR-06，不再原样落库）。
     */
    private String validateAndNormalizeTags(String raw) {
        if (raw == null) {
            // null=不修改（与其它可选字段一致，保护「仅传 status 的行内部分更新」不误清标签）
            return null;
        }
        java.util.LinkedHashSet<String> parts = new java.util.LinkedHashSet<>();
        for (String part : raw.split("[,，]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        for (String tag : parts) {
            if (!DishConst.VALID_TAGS.contains(tag)) {
                throw new BusinessException("标签值非法：" + tag
                        + "（仅允许 " + DishConst.TAG_RECOMMENDED + " / " + DishConst.TAG_SIGNATURE + "）");
            }
        }
        return parts.isEmpty() ? "" : String.join(",", parts);
    }

    /** 辣度值域校验（P0-01 / PR-06）：0-3（含），越界 400。null=不修改 */
    private void validateSpiceLevel(Integer spiceLevel) {
        if (spiceLevel == null) {
            return;
        }
        if (spiceLevel < DishConst.SPICE_LEVEL_MIN || spiceLevel > DishConst.SPICE_LEVEL_MAX) {
            throw new BusinessException("辣度取值非法：" + spiceLevel
                    + "（仅允许 " + DishConst.SPICE_LEVEL_MIN + "-" + DishConst.SPICE_LEVEL_MAX + "）");
        }
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
