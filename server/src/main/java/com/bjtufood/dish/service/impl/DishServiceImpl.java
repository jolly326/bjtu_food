package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.common.config.CacheConfig;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.PageUtil;
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
import com.bjtufood.review.mapper.ReviewMapper;
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
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DishServiceImpl implements DishService {

    /** 搜索别名最大长度（与 schema.sql dish.alias VARCHAR(255) 对齐，含逗号分隔符） */
    private static final int ALIAS_MAX_LENGTH = 255;

    /** view_log.target_type 值：菜品（与 ViewLog 实体注释 / HistoryServiceImpl 写入口径一致） */
    private static final String VIEW_TARGET_TYPE_DISH = "dish";

    /**
     * 「空值语义」的食堂/档口名称集合（§7.23 第 1 条：upsert 时这类名称视为未填，不建档）。
     * 命中即回退 stallId 逻辑，绝不以其为名新建食堂/档口。
     */
    private static final Set<String> EMPTY_NAME_VALUES = Set.of("其他", "其它", "无", "未知");

    /** 新建档口时未提供有效所属食堂的报错文案（与 web 端「食堂必填」契约一致） */
    private static final String MSG_CANTEEN_REQUIRED = "请选择所属食堂";

    private final DishMapper dishMapper;
    private final StallMapper stallMapper;
    private final CanteenMapper canteenMapper;
    private final ReviewMapper reviewMapper;
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
    public DishVO getDishDetail(Long id) {
        DishDetailVO vo = dishMapper.selectDishDetail(id);
        if (vo == null) {
            throw new BusinessException("菜品不存在");
        }

        // 从 images_json 解析 images（避免二次查数据库）
        enrichImages(vo);

        // 查询评分分布
        List<RatingDistributionVO> distribution = dishMapper.selectRatingDistribution(id);
        vo.setRatingDistribution(fillRatingDistribution(distribution));

        // hasReviewed（当前用户是否已评价）已于 2026-09-15 下线（三端零消费，连带删除字段与取值查询）；
        // 2026-09-16：详情已无任何登录态字段，userId 入参随之收口（Controller 不再解析登录态，
        // 端点路径与响应结构零变化）。
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
     * 去重真源为 view_log 表（user_id + target_type='dish' + target_id 的 updated_at 落在
     * 「当天」，自然日按 Asia/Shanghai 切分），与原 5 分钟内存窗口
     * （{@link ViewRateLimiter}，多用于吸收短时间内的刷新抖动）叠加生效：
     * <ol>
     *   <li>5 分钟窗口命中 → 立即返回（省去一次 DB 查询）；</li>
     *   <li>当日已存在浏览记录 → 幂等返回成功：<b>不自增 view_count，也不重复插记录</b>；</li>
     *   <li>否则写入/刷新一条 view_log 并执行原子自增（足迹行即「当日已计」的判据）。</li>
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
        // 写入/刷新浏览足迹（upsert：已存在则刷新 updated_at，作为次日起的新判据）；游客不记录（内部判空）
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
                .map(this::enrichImages)
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
        // 解析档口归属（§7.23 第 1 条：支持按名 upsert 食堂/档口；新增路径必须得到有效档口）
        Long stallId = resolveStallId(req);
        if (stallId == null) {
            throw new BusinessException("档口不存在");
        }
        Dish dish = new Dish();
        applyReq(dish, req);
        // 按名 upsert 解析出的档口可能不同于 req.stallId（stallName 有效时优先），在 applyReq 之后回填
        dish.setStallId(stallId);
        dish.setAvgRating(BigDecimal.ZERO);
        dish.setRatingCount(0);
        dish.setViewCount(0);
        if (!StringUtils.hasText(dish.getStatus())) {
            dish.setStatus(DishConst.STATUS_ON);
        }
        // 注：菜品审核语义已整体退役（dish.audit_status 列与写入同批移除，2026-09-15 阶段4）——
        // 管理员即权威，录入/编辑后菜品直接生效，「落库默认值导致新菜不可见」的顾虑不再存在。
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
        // 编辑路径按名 upsert（§7.23 第 1 条）：stallName 有效时解析/建档并覆盖档口；
        // 未传有效名称时回退 stallId（null=不修改；非 null 则校验存在，与新增路径同口径）
        Long stallId = resolveStallId(req);
        applyReq(dish, req);
        if (stallId != null) {
            dish.setStallId(stallId);
        }
        // 同上（2026-09-15 阶段4）：审核语义退役后编辑路径不再回写审核态，
        // 「改了信息反而从端上消失」的隐患随 audit_status 列下线一并消除。
        dishMapper.updateById(dish);
        // 契约约定：null/0 表示清空可空的原价（applyReq 已把 0 归一为 null 并写回实体）；
        // updateById 默认 NOT_NULL 策略不落 null，需显式置空
        boolean clearOriginalPrice = dish.getOriginalPrice() == null;
        // alias 契约与原价不同：null=不修改（保护「仅传 status 的行内部分更新」不误清别名）；
        // 传了字段（含空串/纯空白）但规范化后为空 = 清空别名，updateById 不落 null，需显式置空。
        boolean clearAlias = req.getAlias() != null
                && !StringUtils.hasText(normalizeAlias(req.getAlias()));
        if (clearOriginalPrice || clearAlias) {
            LambdaUpdateWrapper<Dish> clearWrapper = new LambdaUpdateWrapper<Dish>().eq(Dish::getId, id);
            if (clearOriginalPrice) {
                clearWrapper.set(Dish::getOriginalPrice, null);
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
        // 级联清理该菜品下的全部评价（BE-108）
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
        // 计入口径（Q-110 / 2026-09-15 归一）：仅 is_hidden=0 的评价计入（sec_state 已全链退役，
        // 内容安全检测 pass/review 一律放行、risky 拒绝不入库）；口径真源在 DishMapper.xml
        // recalcRatingBySubquery。全量重算与增量路径（新增/删除/隐藏）统一走本方法。
        dishMapper.recalcRatingBySubquery(dishId);
    }

    /**
     * 解析菜品归属档口（§7.23 第 1 条：食堂/档口是菜品属性，随菜品按名 upsert，不独立建档）。
     * <p>
     * 优先级：
     * <ol>
     *   <li>{@code stallName} 传了有效名称（非空白且不在 {@link #EMPTY_NAME_VALUES} 空值集合内）：
     *       按名查字典——命中同名档口则复用其 ID（同名不重复建档）；未命中则自动建档
     *       （建档时 {@code canteenName} 必须为有效名称并按名 upsert 所属食堂，
     *       空值/未传 → 400「请选择所属食堂」，不允许落 canteen_id=0）。</li>
     *   <li>否则回退 {@code stallId}：null=不修改（编辑路径部分更新语义）；非 null 时校验档口存在，
     *       不存在 400（新增/编辑同口径，PR-06）。</li>
     * </ol>
     *
     * @return 解析后的档口 ID；null 仅在「无有效 stallName 且 stallId 未传」时出现（新增路径上游已拦截为 400）
     */
    private Long resolveStallId(DishAdminReq req) {
        String stallName = normalizeUpsetName(req.getStallName());
        if (stallName != null) {
            return upsertStallByName(stallName, req.getCanteenName());
        }
        if (req.getStallId() != null && stallMapper.selectById(req.getStallId()) == null) {
            throw new BusinessException("档口不存在");
        }
        return req.getStallId();
    }

    /**
     * 按名 upsert 档口：同名不重复建档（精确匹配，名称列无唯一键，并发双写极端情况由调用方幂等容忍）。
     * <p>
     * 新建档口必须有可解析的有效所属食堂（canteenName 有效），否则 400「请选择所属食堂」——
     * 不允许落 canteen_id=0（未挂食堂）：joinDishSql 对 stall/canteen 为 INNER JOIN，
     * canteen_id=0 的菜品会被列表/详情查询静默剔除（2026-09-15 收口，与 web 端「食堂必填」契约一致）。
     */
    private Long upsertStallByName(String stallName, String rawCanteenName) {
        Stall existing = stallMapper.selectOne(new LambdaQueryWrapper<Stall>()
                .eq(Stall::getName, stallName)
                .last("LIMIT 1"));
        if (existing != null) {
            // 同名档口已存在：直接复用（canteenName 仅在新建档口时消费，不迁移既有档口归属）
            return existing.getId();
        }
        Long canteenId = upsertCanteenIdByName(rawCanteenName);
        if (canteenId == null) {
            // 新建档口必须挂有效食堂：拦截在写入前，杜绝 canteen_id=0 的不可见脏数据
            throw new BusinessException(MSG_CANTEEN_REQUIRED);
        }
        Stall stall = new Stall();
        stall.setName(stallName);
        stall.setCanteenId(canteenId);
        stallMapper.insert(stall);
        return stall.getId();
    }

    /**
     * 按名 upsert 食堂（仅当新建档口时消费）：有效名称查字典命中则复用，未命中自动建档。
     * <p>
     * 空白/「其他」等空值语义名称 <b>不建档也不落 0</b>，返回 null 由调用方 400 拦截
     * （2026-09-15 收口：旧逻辑返回 0L 会产生 canteen_id=0 的档口，其菜品被
     * joinDishSql 的 INNER JOIN 静默剔除，属隐性数据丢失）。
     *
     * @return 食堂 ID；null=无可解析的有效食堂名（调用方必须 400，不得写库）
     */
    private Long upsertCanteenIdByName(String rawCanteenName) {
        String canteenName = normalizeUpsetName(rawCanteenName);
        if (canteenName == null) {
            return null;
        }
        Canteen existing = canteenMapper.selectOne(new LambdaQueryWrapper<Canteen>()
                .eq(Canteen::getName, canteenName)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing.getId();
        }
        Canteen canteen = new Canteen();
        canteen.setName(canteenName);
        canteenMapper.insert(canteen);
        return canteen.getId();
    }

    /**
     * upsert 名称规范化：trim 后为空白或命中 {@link #EMPTY_NAME_VALUES}（「其他」等空值语义）返回 null（不建档）；
     * 其余返回 trim 后的名称。
     */
    private static String normalizeUpsetName(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || EMPTY_NAME_VALUES.contains(trimmed)) {
            return null;
        }
        return trimmed;
    }

    /**
     * 请求体 → 实体写入（新增与编辑共用）。
     * <p>
     * 可选字段（price/originalPrice/description/dietType/ingredients/flavorTags/serveTemp/status）
     * 遵循「null=不修改」语义：编辑路径 MyBatis-Plus {@code updateById} 默认 NOT_NULL 策略会跳过 null 字段，
     * 新增路径 null 则落库列默认值（与既有 applyReq 风格一致）。
     * <p>
     * 所有值域校验集中在此（PR-06：非法入参必须 400 报错，不得静默降级落库）。
     * 价格口径（2026-09-20 拍板 D2）：唯一数据源为 {@code price}（现价，已含折扣），
     * {@code originalPrice} 为可空原价；不再存在 promo_price 第三价格字段。
     */
    private void applyReq(Dish dish, DishAdminReq req) {
        dish.setStallId(req.getStallId());
        dish.setName(req.getName());
        String alias = normalizeAlias(req.getAlias());
        if (alias != null && alias.length() > ALIAS_MAX_LENGTH) {
            throw new BusinessException("搜索别名过长（含逗号分隔符最多 " + ALIAS_MAX_LENGTH + " 字符）");
        }
        dish.setAlias(alias);

        // 金额值域校验与归一化（P1-05）：单位仍为「分」，仅加值域约束，不改量纲。
        // price 为必填价格：非 null 时必须 > 0，0/负数 → 400。
        // originalPrice 为可空原价：web 以 0 表示「无原价」（schema 用 NULL 表达该语义），故 0 归一为 null，负数 → 400。
        Integer price = req.getPrice();
        if (price != null && price <= 0) {
            throw new BusinessException("价格必须大于 0（单位：分）");
        }
        dish.setPrice(price);
        dish.setOriginalPrice(normalizeDiscount(req.getOriginalPrice(), "原价"));

        dish.setDescription(req.getDescription());
        dish.setImages(JsonListUtil.toJson(req.getImages()));

        // 描述四维（§7.28 定型）：荤素 / 主料 / 口味 / 冷热（原辣度 spice_level、风味 region 已下线）
        dish.setDietType(req.getDietType());
        dish.setIngredients(req.getIngredients());
        dish.setFlavorTags(req.getFlavorTags());
        dish.setServeTemp(req.getServeTemp());

        dish.setStatus(req.getStatus());
    }

    /**
     * 可空价格字段（原价，单位：分）归一化与值域校验（P1-05）。
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
     * 将数据库原始的 imagesJson 解析为绝对 URL 列表并回填到 images。
     * DishVO 与 DishAdminVO 共用同一套转换逻辑，故统一方法名 enrichImages，以重载区分。
     */
    private List<String> resolveImages(String imagesJson) {
        return imageUrlUtil.parseAndToAbsoluteUrls(imagesJson);
    }

    private DishVO enrichImages(DishVO vo) {
        if (vo == null) {
            return null;
        }
        vo.setImages(resolveImages(vo.getImagesJson()));
        return vo;
    }

    private DishAdminVO enrichImages(DishAdminVO vo) {
        if (vo == null) {
            return null;
        }
        vo.setImages(resolveImages(vo.getImagesJson()));
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
