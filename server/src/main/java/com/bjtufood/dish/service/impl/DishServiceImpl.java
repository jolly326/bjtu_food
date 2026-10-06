package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.PageUtil;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.common.utils.JsonMapUtil;
import com.bjtufood.common.utils.ParamValidator;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.dto.DishAttributeEditVO;
import com.bjtufood.dish.dto.DishAttributeItem;
import com.bjtufood.dish.dto.DishAdminListItemVO;
import com.bjtufood.dish.dto.DishAdminListQuery;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishCategoryAdminVO;
import com.bjtufood.dish.dto.DishHealthVO;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishListItemVO;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.constant.DishConst;
import com.bjtufood.dish.dto.DishCorrectionCmd;
import com.bjtufood.dish.dto.GuessLikeVO;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.entity.DishAttributeDimension;
import com.bjtufood.dish.entity.DishViewLog;
import com.bjtufood.dish.event.DishDeletedEvent;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.mapper.DishViewLogMapper;
import com.bjtufood.dish.service.DishAttributeAdminService;
import com.bjtufood.dish.service.DishAttributeCatalog;
import com.bjtufood.dish.entity.DishFilterView;
import com.bjtufood.dish.service.DishCategoryAdminService;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.dish.service.DishViewCatalog;
import com.bjtufood.dish.view.DishListQuery;
import com.bjtufood.dish.view.DishViewResolver;
import com.bjtufood.dish.view.DishViewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DishServiceImpl implements DishService {

    /**
     * 猜你喜欢返回条数。
     * <p>
     * 端上不写死条数、不截断、不排序，一律按返回渲染——**条数上限是数据源侧职责**。
     * 收为 6 的理由（见 docs/ui/client/搜索.md §1 第 4 条）：该接口当前是**纯随机**推送
     * （无推荐算法），8 条会占满发现态首屏（实测排成 3 行 chips），把「搜索记录」这个
     * 真正的个性化入口挤出可视区。
     */
    private static final int GUESS_LIKE_SIZE = 6;

    /** 菜品图片上限（A3：0~5 张、有序、首图作封面；0 张合法，client 有统一占位图） */
    private static final int DISH_IMAGE_MAX = 5;

    /**
     * 图片 JSON 序列化后的长度上限 —— 与 {@code dish.images VARCHAR(1024)} 对齐。
     * 不校验的话会直接撞上列宽（MySQL 严格模式报错 / 非严格模式**静默截断**，后者更危险）。
     */
    private static final int DISH_IMAGES_JSON_MAX = 1024;

    private final DishMapper dishMapper;
    /** 浏览明细日志写入 + 管理端「近 30 天浏览」查询（🔴 不参与排序，见 {@code DishViewLogMapper}） */
    private final DishViewLogMapper dishViewLogMapper;
    private final StallService stallService;
    /**
     * 事件发布器：菜品删除后的评价级联清理由 review 域监听器承接
     * （P0-1 跨域写侧解耦，dish 域不注入 ReviewMapper / ReviewService）。
     */
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUrlUtil imageUrlUtil;
    /**
     * 描述属性的目录数据（维度字典 + 编辑候选值），带缓存。
     * <p>
     * 之所以是<b>注入的协作对象</b>而不是本类的私有方法：声明式缓存靠代理生效，
     * 自调用不过代理，注解会静默失效。缓存放行与否的性能差异见
     * {@code DishCacheBenchmarkTest}（同一份口径可直接对比）。
     */
    private final DishAttributeCatalog attributeCatalog;

    /**
     * 属性取值字典（A4）：负责「入参 ID/中文 → 取值 ID」与「出参 取值 ID → 中文」两向转换，
     * 以及中文名未命中时的**自动登记**。
     */
    private final DishAttributeAdminService attributeAdminService;

    /**
     * 菜品分类值字典（A6）：负责「输入新分类 → 自动登记」与写入前的键规范化。
     */
    private final DishCategoryAdminService categoryAdminService;

    /**
     * 首页筛选视图目录（A6 表驱动）：`GET /dishes/views` 的下发规则与 `view=` 参数的查表校验。
     */
    private final DishViewCatalog viewCatalog;

    @Override
    public IPage<DishListItemVO> listDishes(DishQueryReq req) {
        if (req == null) {
            req = new DishQueryReq();
        }
        // 统一走 PageUtil.normalize（null 先兜底为 0 交由工具类归一化），与其他分页入口保持一致
        int[] norm = PageUtil.normalize(
                req.getPage() == null ? 0 : req.getPage(),
                req.getPageSize() == null ? 0 : req.getPageSize());
        req.setPage(norm[0]);
        req.setPageSize(norm[1]);
        // 视图解析（查表取展示态 + 按 key 取代码逻辑，PR-06）：空值 = 首个启用视图（按 `order` 升序）；
        // 未登记 / 逻辑无定义的键 400 报错，不静默降级。
        // 筛选条件与排序口径由代码常量 DishViewDefs 按 key 决定（见 DishViewResolver 与 DishViewConditions），
        // API 层不感知 meal_type / 价格等字段，端上也无排序入口。
        DishFilterView view = viewCatalog.byKey(req.getView());
        DishListQuery query = DishViewResolver.resolve(view, req.getKeyword(), req.getSeed());
        if (query == null) {
            throw new BusinessException("筛选视图不合法：" + req.getView());
        }
        // 列表出参为 DishListItemVO（8 字段）：图片只下发首图 coverImage，由 enrichCoverImage 从 imageUrls 取首图
        return dishMapper.selectDishPage(new Page<>(req.getPage(), req.getPageSize()), query)
                .convert(this::enrichCoverImage);
    }

    @Override
    // 表驱动（A6）：视图目录与「enabled / 匹配数为 0 不下发」规则全部收在 DishViewCatalog；
    // **缓存也随之上移**（DISH_VIEWS 由目录持有，写侧显式失效 ⇒ 保存即生效）。
    // 出参仍是 [{key, label}]，客户端契约不变。
    public List<DishViewVO> listDishViews() {
        return viewCatalog.visible().stream()
                .map(v -> new DishViewVO(v.getKey(), v.getLabel()))
                .toList();
    }

    /**
     * 菜品描述属性编辑态候选（{@code GET /dishes/{id}/attributes}，按需）。
     * <p>
     * 只返回该菜<b>现有维度</b>（{@code dish.attributes} 的键集合 ∩ 维度字典，按维度 {@code order} 升序），
     * 只补编辑要用的 {@code valueType} + 参考候选 {@code options}
     * （候选 = 该维度「全库已用中文值」去重、按使用频次倒序；仅为参考、不构成约束）。
     */
    @Override
    public List<DishAttributeEditVO> listDishAttributes(Long dishId) {
        // 单次取行同时完成「存在且在售」判定与属性读取 —— 拆成「存在性校验 + 单独取 attributes」
        // 会在远程库上叠加两次 RTT。
        Dish dish = dishId == null ? null : dishMapper.selectById(dishId);
        // 不存在与已下架同款处理（与详情口径一致）
        if (dish == null || !DishConst.STATUS_ON.equals(dish.getStatus())) {
            throw new BusinessException(4001, "菜品不存在");
        }
        // 当前值出参同样是中文（翻译 ID），端上编辑弹层直接渲染
        Map<String, Object> raw = attributeAdminService.translateForRead(dish.getAttributes());
        if (raw.isEmpty()) {
            return List.of();
        }
        // 目录数据（维度字典 + 候选值聚合）由独立 bean 提供：直接调用即可命中其上的 @Cacheable，
        // 缓存口径与失效时机见 DishAttributeCatalog 的类注释。
        Map<String, List<String>> candidates = attributeCatalog.candidateValuesByFieldKey();
        return attributeCatalog.dimensions().stream()
                .filter(dim -> raw.containsKey(dim.getFieldKey()))
                .map(dim -> new DishAttributeEditVO(
                        dim.getFieldKey(),
                        dim.getValueType(),
                        candidates.getOrDefault(dim.getFieldKey(), List.of())))
                .toList();
    }

    /**
     * 把 {@code dish.attributes} 的 JSON 原文整理为出参 {@code attributes[]}（值即中文）。
     * <p>
     * 自描述：只含该菜品实际拥有的维度，按维度 {@code order} 升序；某维度无值则不出现、不占位。
     */
    private List<DishAttributeItem> buildAttributeItems(String attributesJson,
                                                        List<DishAttributeDimension> dimensions) {
        // 库里存的是**取值 ID**（A4）⇒ 先翻译为中文，再按维度组织展示项
        Map<String, Object> raw = attributeAdminService.translateForRead(attributesJson);
        if (raw.isEmpty() || dimensions.isEmpty()) {
            return List.of();
        }
        List<DishAttributeItem> items = new ArrayList<>();
        for (DishAttributeDimension dim : dimensions) {
            if (!raw.containsKey(dim.getFieldKey())) {
                continue;
            }
            Object value = raw.get(dim.getFieldKey());
            if (value == null) {
                continue;
            }
            items.add(new DishAttributeItem(dim.getFieldKey(), dim.getName(), value));
        }
        return items;
    }

    /**
     * 菜品详情（含**浏览计数副作用**）。
     * <p>
     * 计数口径（PV）：本方法**成功取到详情后**向 {@code dish_view_log} 插一行明细
     * （<b>不去重</b>：同一用户反复看同一道菜每次都计），不累加 {@code dish.view_count}（该列已停写）。
     * 菜品不存在 / 已下架（vo == null）抛 {@code BusinessException(4001)}，不计数。
     * <p>
     * 🔴 **浏览量不参与排序**（2026-10-05，热度算法全量下线）—— 本方法只负责记录数据，
     * 展示方仅管理端「近 30 天浏览」列（见 {@code docs/schema/dish_view_log.md}）。
     * <p>
     * 事务：查询与计数写操作同处一个事务 —— 计数失败则详情一并失败，
     * 保证「返回 200 的响应必然已计数」的口径自洽。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishDetailVO getDishDetail(Long id) {
        // 不存在与已下架**同款处理**（change dish-detail-contract-hardening R8）：
        // SQL 已按 status='on' 过滤，故「已下架」在此同样落为 vo == null —— 对公开接口而言
        // 「下架」等价于「不存在」，不设专用字段 / 专用分支。
        // 4001 = 资源不存在（细分业务码，端上据此直接给恢复路径，无需解析 message 文本；
        // 与 4031「邮箱未认证」同为细分码）
        DishDetailVO vo = dishMapper.selectDishDetail(id);
        if (vo == null) {
            throw new BusinessException(4001, "菜品不存在");
        }

        // ===== 浏览计数副作用（先于返回值组装，成功路径恒执行）=====
        // 🔴 不写 dish.view_count（该列停写，历史累计值仅作参考），浏览明细写入 dish_view_log，
        //    支撑管理端「近 30 天浏览量」滚动窗口（见 docs/schema/dish_view_log.md §2.4）。
        // 不去重（PV 口径）：同一用户反复看同一道菜每次都计 —— MVP 期浏览量不参与排序，被刷无用户可见危害，
        //    去重需引入 user_id 列与唯一约束，写入成本与锁竞争显著上升，收益当前无消费方。
        DishViewLog viewLog = new DishViewLog(id, LocalDateTime.now());
        int affected = dishViewLogMapper.insert(viewLog);
        if (affected == 0) {
            throw new BusinessException(4001, "菜品不存在");
        }
        // 多图 → 绝对 URL（图片列已由 TypeHandler 直出为 List）
        enrichImages(vo);

        // 描述属性：JSON 原文即中文值 → 展示项（R4，端上零翻译）；维度字典走独立 bean（带缓存）
        vo.setAttributes(buildAttributeItems(vo.getAttributesJson(), attributeCatalog.dimensions()));

        // avgRating 恒读缓存列 dish.avg_rating（由 selectDishDetail 一并查出），零评价（NULL）时出参兜底为 5.0
        // （冷启动展示口径，见 docs/api/client/dishes.md）；兜底只在出参层：不落库、不参与排序
        // （🔴 2026-10-05 热度算法全量下线，当前 7 个视图均走会话伪随机序，本兜底值无排序消费方；
        //   后期重启热度时仍必须保持此分离，否则零评价菜会凭满分权重虚高置顶）。
        vo.setAvgRating(vo.getAvgRating() != null ? vo.getAvgRating() : DishConst.ZERO_RATING_FALLBACK);

        // 详情出参不含任何登录态字段（无 hasReviewed 等用户态分支）。
        return vo;
    }

    /**
     * 猜你喜欢：按端上下发的**会话级** seed 做稳定伪随机取数
     * 。
     * <p>
     * 语义澄清：用户侧<b>无「主动换一批」入口</b>（全仓无下拉刷新），内容却在会话内自变 ⇒
     * 体验是「界面不稳定」而非「新鲜」；故随机性归于会话——seed 会话内恒定、冷启动才重掷，
     * 同一次会话内多次进入拿到同一批词条，重进小程序整体重洗。
     * <p>
     * 仍<b>不加响应缓存</b>：seed 已把「会话内稳定」表达在数据层；若再加 TTL 型缓存，
     * 缓存键必须含 seed 才有意义（否则不同会话互相串味），收益与复杂度不成正比。
     * 未传 seed 的调用（第三方 / 直连 Swagger / 旧端）由本方法补一次性随机值，
     * 因此无论是否传 seed，**每次调用都不同** —— 与缓存语义必然冲突，更不能缓存。
     */
    @Override
    public List<GuessLikeVO> guessLike(String seed) {
        // 缺省 seed 时在此补一个一次性随机值，而不是把「随机」下推到 SQL 的 ORDER BY RAND()：
        // 后者是全表排序（代价随行数增长、无法用索引），而补 seed 后同一套 CRC32 稳定伪随机序
        // 就能覆盖旧行为，契约（未传 seed ⇒ 每次不同的随机序）不变。
        String effectiveSeed = StringUtils.hasText(seed) ? seed : UUID.randomUUID().toString();
        return dishMapper.selectGuessLike(GUESS_LIKE_SIZE, effectiveSeed);
    }

    @Override
    public Map<Long, Long> mapStallIdByIds(Collection<Long> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return Map.of();
        }
        List<Dish> rows = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .select(Dish::getId, Dish::getStallId)
                .in(Dish::getId, dishIds));
        Map<Long, Long> result = new HashMap<>(rows.size());
        for (Dish row : rows) {
            if (row.getStallId() != null) {
                result.put(row.getId(), row.getStallId());
            }
        }
        return result;
    }

    @Override
    public IPage<DishAdminListItemVO> listAllForAdmin(DishAdminListQuery query, int page, int pageSize) {
        // 分页上限统一由 PageUtil 约束，避免一次性全表加载
        int[] norm = PageUtil.normalize(page, pageSize);
        page = norm[0];
        pageSize = norm[1];
        // 上架状态筛选走白名单（非法值 400，不静默恒空 —— 否则会掩盖真实数据）
        DishAdminListQuery effective = new DishAdminListQuery(
                query == null ? null : query.stallId(),
                query == null ? null : query.canteenId(),
                query == null ? null : query.mealType(),
                query == null ? null
                        : ParamValidator.optionalInWhitelist(query.status(), DishConst.QUERY_STATUSES, "上架状态"),
                query == null || !StringUtils.hasText(query.keyword()) ? null : query.keyword().trim());
        // A3：列表走**瘦身列表 VO**（不含 description / attributes / 全量 images）
        IPage<DishAdminListItemVO> result = dishMapper.selectAdminListPage(new Page<>(page, pageSize), effective);
        // 分类中文名（A6 分类值字典）：列表直接可读，端上零硬编码；字典量级极小，逐页取一次
        Map<String, String> mealTypeLabels = categoryAdminService.listAll().stream()
                .collect(Collectors.toMap(DishCategoryAdminVO::getKey, DishCategoryAdminVO::getLabel,
                        (a, b) -> a));
        // 近 30 天浏览量：一次性按当前页 dish_id 批量聚合（BE 惯用法，避免 N+1）；
        // 🔴 走 dish_view_log 滚动窗口而非 dish.view_count（后者已停写且是历史累计，语义不同）
        Map<Long, Long> recentViews = loadRecentViewCounts(result.getRecords());
        return PageUtil.toVoPage(result, recs -> recs.stream()
                .map(vo -> {
                    vo.setMealTypeLabel(mealTypeLabels.getOrDefault(
                            vo.getMealType() == null ? "" : vo.getMealType(), ""));
                    vo.setCoverImage(toAbsoluteCover(vo.getCoverImage()));
                    // 无浏览记录的菜品不在聚合结果里 ⇒ 显式补 0（端上不渲染「—」）
                    vo.setRecentViewCount(recentViews.getOrDefault(vo.getId(), 0L));
                    return vo;
                })
                .toList());
    }

    /** 「近 30 天浏览量」滚动窗口天数（与 {@code DishViewLogCleanupTask.RETENTION_DAYS} 一致） */
    private static final int RECENT_VIEW_WINDOW_DAYS = 30;

    /**
     * 批量取当前页菜品的近 30 天浏览量。
     *
     * <p>一次性 {@code COUNT(*) GROUP BY dish_id} 取回本页全部，避免逐行查询（N+1）。
     * 空页 / 空结果直接返回空 Map，跳过 SQL。
     *
     * <p>Mapper 返回 {@code List<Row>}（规避 MyBatis 的 {@code Map} 返回类型歧义，见 Mapper 注释），
     * 此处转成 {@code dishId -> viewCount} 便于 O(1) 查表。
     */
    private Map<Long, Long> loadRecentViewCounts(List<DishAdminListItemVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(DishAdminListItemVO::getId).toList();
        List<DishViewLogMapper.Row> counts =
                dishViewLogMapper.countRecentViewsByDishIds(ids, RECENT_VIEW_WINDOW_DAYS);
        if (counts == null || counts.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> byDishId = new HashMap<>(counts.size());
        for (DishViewLogMapper.Row row : counts) {
            if (row.getDishId() != null && row.getViewCount() != null) {
                byDishId.put(row.getDishId(), row.getViewCount());
            }
        }
        return byDishId;
    }

    /** 封面：相对路径 → 绝对 URL（空串原样返回，端上按统一占位图呈现） */
    private String toAbsoluteCover(String cover) {
        if (!StringUtils.hasText(cover)) {
            return "";
        }
        List<String> absolute = imageUrlUtil.toAbsoluteUrls(List.of(cover));
        return absolute.isEmpty() ? "" : absolute.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishAdminVO addDish(DishAdminReq req) {
        // 新增必填校验（DTO 层已放开以支持部分更新，必填在此兜底）
        if (!StringUtils.hasText(req.getName())) {
            throw new BusinessException("菜品名称不能为空");
        }
        if (req.getPrice() == null) {
            throw new BusinessException("价格不能为空");
        }
        // 产品定型：菜品首图必填（无图不录入 / 不上架；已上架的老数据不受影响）+ A3 图片 0~5 张与列宽校验
        validateImages(req.getImages(), "请至少上传 1 张菜品图");
        // 解析档口归属（支持按名 upsert 食堂/档口；新增路径必须得到有效档口）
        Long stallId = resolveStallId(req);
        if (stallId == null) {
            throw new BusinessException("档口不存在");
        }
        Dish dish = new Dish();
        applyReq(dish, req);
        // 按名 upsert 解析出的档口可能不同于 req.stallId（stallName 有效时优先），在 applyReq 之后回填
        dish.setStallId(stallId);
        // avg_rating 保持 NULL（零评价）；公开出参由 ZERO_RATING_FALLBACK 兜底为 5.0（仅出参层，不落库）
        dish.setRatingCount(0);
        dish.setViewCount(0);
        if (!StringUtils.hasText(dish.getStatus())) {
            dish.setStatus(DishConst.STATUS_ON);
        }
        // 注：菜品无审核语义（dish 表无 audit_status 列，写入面也不含审核态）——
        // 管理员即权威，录入/编辑后菜品直接生效。
        dishMapper.insert(dish);
        // 新菜带来的属性取值会进入「编辑候选值」的全库去重结果 → 显式失效，避免新值最长 2 分钟不可见
        attributeCatalog.invalidateCandidates();
        // 契约（A3）:POST /admin/dishes 返回**新建的 VO**（时间列以回查为准）
        return toAdminVO(dishMapper.selectById(dish.getId()));
    }

    @Override
    public DishHealthVO countHealth() {
        DishHealthVO vo = new DishHealthVO();
        vo.setOnSaleCount(dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStatus, DishConst.STATUS_ON)));
        // 三个「缺失」项统计**全部菜品（含下架）**：健康度是存量清理指标，下架菜品的缺失同样要修
        vo.setWithoutImage(dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .and(w -> w.isNull(Dish::getImages).or().apply("JSON_LENGTH(images) = 0"))));
        vo.setWithoutStall(dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .and(w -> w.isNull(Dish::getStallId).or().eq(Dish::getStallId, 0L))));
        vo.setWithoutCategory(dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .and(w -> w.isNull(Dish::getMealType).or().eq(Dish::getMealType, ""))));
        return vo;
    }

    @Override
    public long countByStallId(Long stallId) {
        if (stallId == null) {
            return 0L;
        }
        return dishMapper.selectCount(new LambdaQueryWrapper<Dish>().eq(Dish::getStallId, stallId));
    }

    @Override
    public Map<Long, Long> countByStallIds(Collection<Long> stallIds) {
        if (stallIds == null || stallIds.isEmpty()) {
            return Map.of();
        }
        List<DishMapper.StallDishCountRow> counts = dishMapper.countByStallIds(stallIds);
        if (counts == null || counts.isEmpty()) {
            return Map.of();
        }
        // Mapper 返回 List<Row>（规避 MyBatis 的 Map 返回类型歧义，见 Mapper 注释），
        // 此处转成 stallId -> dishCount 便于调用方 O(1) 查表
        Map<Long, Long> byStallId = new HashMap<>(counts.size());
        for (DishMapper.StallDishCountRow row : counts) {
            if (row.getStallId() != null && row.getDishCount() != null) {
                byStallId.put(row.getStallId(), row.getDishCount());
            }
        }
        return byStallId;
    }

    @Override
    public DishAdminVO getForAdmin(Long id) {
        Dish dish = id == null ? null : dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException(4001, "菜品不存在");
        }
        return toAdminVO(dish);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishAdminVO copyDish(Long id, String name) {
        Dish source = id == null ? null : dishMapper.selectById(id);
        if (source == null) {
            throw new BusinessException(4001, "菜品不存在");
        }
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("菜品名称不能为空");
        }
        if (name.trim().length() > 64) {
            throw new BusinessException("菜品名称不能超过 64 字");
        }
        Dish copy = new Dish();
        copy.setStallId(source.getStallId());
        copy.setName(name.trim());
        copy.setPrice(source.getPrice());
        copy.setOriginalPrice(source.getOriginalPrice());
        copy.setDescription(source.getDescription());
        copy.setImages(source.getImages());
        copy.setAttributes(source.getAttributes());
        copy.setMealType(source.getMealType());
        // 副本是「半成品」：默认下架，确认内容后再上架（见 A3 备注）
        copy.setStatus(DishConst.STATUS_OFF);
        // 评价 / 浏览量归零、均分置空（副本自带独立评分聚合）
        copy.setRatingCount(0);
        copy.setViewCount(0);
        copy.setAvgRating(null);
        dishMapper.insert(copy);
        attributeCatalog.invalidateCandidates();
        return toAdminVO(dishMapper.selectById(copy.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        if (!DishConst.STATUS_ON.equals(status) && !DishConst.STATUS_OFF.equals(status)) {
            throw new BusinessException("status 非法");
        }
        Dish update = new Dish();
        update.setId(id);
        update.setStatus(status);
        if (id == null || dishMapper.updateById(update) == 0) {
            throw new BusinessException(4001, "菜品不存在");
        }
    }

    /**
     * 实体 → 管理端 VO（编辑回填 / 新增 / 复制统一出口）。
     * <p>
     * 与列表 VO 的差别见 A3：详情回填要<b>全字段</b>（描述、属性、全量图片），
     * 且图片出参一律转绝对 URL、属性 JSON 还原为对象。
     */
    private DishAdminVO toAdminVO(Dish dish) {
        DishAdminVO vo = new DishAdminVO();
        vo.setId(dish.getId());
        vo.setStallId(dish.getStallId());
        vo.setName(dish.getName());
        vo.setPrice(dish.getPrice());
        vo.setOriginalPrice(dish.getOriginalPrice());
        vo.setDescription(dish.getDescription());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(dish.getImages()));
        vo.setStatus(dish.getStatus());
        vo.setAvgRating(dish.getAvgRating());
        vo.setRatingCount(dish.getRatingCount());
        vo.setCreatedAt(dish.getCreatedAt());
        vo.setUpdatedAt(dish.getUpdatedAt());
        vo.setMealType(dish.getMealType());
        // 出参翻译：库里存的是**取值 ID**（A4）⇒ 统一翻译为中文；找不到对应取值的原样保留
        vo.setAttributes(attributeAdminService.translateForRead(dish.getAttributes()));
        // 归属名称：档口名 + 所属食堂名（管理端编辑回填需要显示，避免再发一次列表请求）
        vo.setStallName(stallService.getNameById(dish.getStallId()));
        vo.setCanteenName(stallService.getCanteenNameByStallId(dish.getStallId()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDish(Long id, DishAdminReq req) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        // ==================== A3：**可编辑字段整体替换**（2026-10-03） ====================
        // 口径：**一条显式 UPDATE 写全可编辑字段** —— 提交什么就是什么，语义单一、无补丁。
        // 不用「updateById 部分更新」+ 第二次 UPDATE 补丁来自清空：那会出现「漏传字段导致旧值残留」，
        // 且需额外补丁才能表达「清空」这一个语义。
        //
        // 「null 的语义」逐字段定死（避免误清空）：
        //   · name / mealType：**必填**（空 → 400）—— 表单式编辑恒会提交；
        //   · originalPrice：0 / null ⇒ **清空**（本次显式 set null）；
        //   · description：null ⇒ 空串（与详情出参「无为空串」同口径）；
        //   · images / attributes / stallId：null ⇒ **不修改**（不传即不动；要清空请传空数组 / 空对象）。
        //     理由：这三类是「结构性内容」，客户端未带时多为**未改动**而非「删除全部」；
        //     误判成清空会造成不可逆的数据丢失。
        //   · status：本端点**不写**（上下架走 PUT /{id}/status）。
        // 首图不可清空：显式传空集合 = 要求清空 ⇒ 拒绝（要下架走 status 端点）；null = 不修改
        validateImages(req.getImages(), "请至少保留 1 张菜品图");
        if (!StringUtils.hasText(req.getName())) {
            throw new BusinessException("菜品名称不能为空");
        }
        if (req.getPrice() == null || req.getPrice() <= 0) {
            throw new BusinessException("价格必须大于 0（单位：分）");
        }
        if (req.getMealType() == null || !StringUtils.hasText(req.getMealType())) {
            throw new BusinessException("请填写菜品分类");
        }

        Long stallId = resolveStallId(req);
        String categoryKey = categoryAdminService.resolveOrRegister(req.getMealType().trim());
        String attributesJson = req.getAttributes() == null ? null
                : toAttributesJson(attributeAdminService.resolveForWrite(req.getAttributes()));

        LambdaUpdateWrapper<Dish> wrapper = new LambdaUpdateWrapper<Dish>()
                .eq(Dish::getId, id)
                .set(Dish::getName, req.getName().trim())
                .set(Dish::getPrice, req.getPrice())
                .set(Dish::getOriginalPrice, normalizeDiscount(req.getOriginalPrice(), "原价"))
                .set(Dish::getDescription, req.getDescription() == null ? "" : req.getDescription())
                .set(Dish::getMealType, categoryKey);
        if (stallId != null) {
            wrapper.set(Dish::getStallId, stallId);
        }
        if (req.getImages() != null) {
            wrapper.set(Dish::getImages, JsonListUtil.toJson(req.getImages()));
        }
        if (attributesJson != null) {
            wrapper.set(Dish::getAttributes, attributesJson);
        }
        dishMapper.update(null, wrapper);
        // 同 addDish：属性写入可能改变候选值集合（详见 DishAttributeCatalog#candidateValuesByFieldKey）
        attributeCatalog.invalidateCandidates();
    }

    /**
     * 图片校验（A3 统一口径，**新增与编辑共用**，避免两处语义漂移）：
     * <ul>
     *   <li>张数 0~5（0 张的取舍见各调用方：`emptyMessage` 非空即「至少 1 张」）；</li>
     *   <li>序列化后长度 ≤ {@value #DISH_IMAGES_JSON_MAX}（对齐列宽，防止静默截断）。</li>
     * </ul>
     *
     * @param images       图片地址列表（**null 一律放行** —— 编辑路径的 null 语义是「不修改」）
     * @param emptyMessage 为空时的报错文案；**null 表示允许为空**
     */
    private static void validateImages(List<String> images, String emptyMessage) {
        if (images == null) {
            return;
        }
        if (images.isEmpty()) {
            if (emptyMessage != null) {
                throw new BusinessException(emptyMessage);
            }
            return;
        }
        if (images.size() > DISH_IMAGE_MAX) {
            throw new BusinessException("菜品图片最多 " + DISH_IMAGE_MAX + " 张");
        }
        String json = JsonListUtil.toJson(images);
        if (json != null && json.length() > DISH_IMAGES_JSON_MAX) {
            throw new BusinessException("图片地址过长（序列化后不得超过 " + DISH_IMAGES_JSON_MAX + " 字符）");
        }
    }

    /** 属性 Map → JSON 原文（空集合落 NULL，表示该菜没有这些属性） */
    private static String toAttributesJson(Map<String, Object> resolved) {
        return resolved == null || resolved.isEmpty() ? null : JsonMapUtil.toJson(resolved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDish(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        // 级联清理该菜品下的全部评价（BE-108）：发布领域事件，由 review 域监听器删除评价行，
        // dish 域不持有 review 表知识（P0-1）。同步监听 → 仍在本次事务内执行，失败一并回滚。
        eventPublisher.publishEvent(new DishDeletedEvent(id));
        dishMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // 评分重算由 RatingUpdateListener 在事务 AFTER_COMMIT 后异步触发，故写库与重算之间无竞态窗口
    // （评分无缓存设施，不存在缓存失效时序问题）
    public void recalcAvgRating(Long dishId) {
        // 并发安全：子查询 AVG/COUNT 整体写回，避免全量查询后回写丢数据。
        // 计入口径（Q-110 / 归一）：仅 is_hidden=0 的评价计入（
        // 内容安全检测 pass/review 一律放行、risky 拒绝不入库）；口径真源在 DishMapper.xml
        // recalcRatingBySubquery。全量重算与增量路径（新增/删除/隐藏）统一走本方法。
        dishMapper.recalcRatingBySubquery(dishId);
    }

    /**
     * 解析菜品归属档口（食堂/档口是菜品属性，随菜品按名 upsert，不独立建档）。
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
        String stallName = req.getStallName() == null ? null : req.getStallName().trim();
        if (StringUtils.hasText(stallName) && !isEmptySemanticName(stallName)) {
            return stallService.upsertStallByName(stallName, req.getCanteenName());
        }
        if (req.getStallId() != null && !stallService.existsById(req.getStallId())) {
            throw new BusinessException("档口不存在");
        }
        return req.getStallId();
    }

    /**
     * 空值语义名称判断（「其他/其它/无/未知」视为未填，回退 stallId 逻辑）。
     * 判据与 {@code StallServiceImpl#upsertStallByName} 内部的名称归一化同源。
     */
    private static boolean isEmptySemanticName(String trimmedName) {
        return Set.of("其他", "其它", "无", "未知").contains(trimmedName);
    }

    /**
     * 请求体 → 实体写入（新增与编辑共用）。
     * <p>
     * 可选字段（price/originalPrice/description/dietType/ingredients/flavorTags/serveTemp/status）
     * 遵循「null=不修改」语义：编辑路径 MyBatis-Plus {@code updateById} 默认 NOT_NULL 策略会跳过 null 字段，
     * 新增路径 null 则落库列默认值（与既有 applyReq 风格一致）。
     * <p>
     * 所有值域校验集中在此（PR-06：非法入参必须 400 报错，不得静默降级落库）。
     * 价格口径：唯一数据源为 {@code price}（现价，已含折扣），
     * {@code originalPrice} 为可空原价；价格字段恰这两个，无 promo_price 第三价格字段。
     */
    private void applyReq(Dish dish, DishAdminReq req) {
        dish.setStallId(req.getStallId());
        dish.setName(req.getName());

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

        // 描述属性（动态属性模型）：JSON 对象，键 = 维度 fieldKey。
        // **值 = 取值 ID**（A4 落地）：入参允许「取值 ID」或「中文名」，中文名同维度内未命中即**自动登记**
        // 为新取值后返回其 ID；未传 = 不修改（NOT_NULL 策略跳过），空对象 = 清空整列。
        if (req.getAttributes() == null) {
            dish.setAttributes(null);
        } else {
            Map<String, Object> resolved = attributeAdminService.resolveForWrite(req.getAttributes());
            dish.setAttributes(resolved.isEmpty() ? null : JsonMapUtil.toJson(resolved));
        }

        // 菜品分类（入库字段；A6 落地后值域 = `dish_category_value` 表）：
        // **输入新分类 → 自动登记**（自由输入产生值域，免发版、免改代码）；仅「空 / 超 20 字 / 非法字符」才 400。
        // null = 不修改（编辑路径 MyBatis-Plus NOT_NULL 策略跳过，行内部分更新不会误清分类）。
        if (req.getMealType() == null) {
            dish.setMealType(null);
        } else if (!StringUtils.hasText(req.getMealType())) {
            throw new BusinessException("菜品分类不能为空");
        } else {
            dish.setMealType(categoryAdminService.resolveOrRegister(req.getMealType().trim()));
        }

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
     * 图片相对路径 → 绝对 URL。
     * <p>
     * 「JSON 串 ↔ List」的转换已下沉到持久层（{@code StringListTypeHandler}），本方法只负责
     * **业务转换**（相对路径 → 可访问绝对 URL）；空值归一为空列表。
     */
    private List<String> toAbsoluteImages(List<String> images) {
        return images == null || images.isEmpty() ? List.of() : imageUrlUtil.toAbsoluteUrls(images);
    }

    /**
     * 列表行封面图：取 {@code imageUrls} 的**首图**填入 {@code coverImage}；
     * 无图时为空串（列表不下发图片数组，故只取首图、不做多图回填）。
     * <p>
     * 同时施加**零评价均分兜底**（{@link DishConst#ZERO_RATING_FALLBACK}）：零评价时
     * {@code avg_rating} 为 NULL，出参下发 {@code 5.0}，避免卡片评分位空缺（仅出参层，不落库）。
     */
    private DishListItemVO enrichCoverImage(DishListItemVO vo) {
        if (vo == null) {
            return null;
        }
        List<String> urls = toAbsoluteImages(vo.getImageUrls());
        vo.setCoverImage(urls.isEmpty() ? "" : urls.get(0));
        if (vo.getAvgRating() == null) {
            vo.setAvgRating(DishConst.ZERO_RATING_FALLBACK);
        }
        return vo;
    }

    private DishDetailVO enrichImages(DishDetailVO vo) {
        if (vo == null) {
            return null;
        }
        vo.setImages(toAbsoluteImages(vo.getImages()));
        return vo;
    }

    private DishAdminVO enrichImages(DishAdminVO vo) {
        if (vo == null) {
            return null;
        }
        vo.setImages(toAbsoluteImages(vo.getImages()));
        return vo;
    }

    // ==================== 跨域契约实现（P0-1：替代 correction / review 的跨域 Mapper 直连） ====================

    @Override
    public boolean existsById(Long dishId) {
        return dishId != null && dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getId, dishId)) > 0;
    }

    @Override
    public boolean existsOnSale(Long dishId) {
        return dishId != null && dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getId, dishId)
                .eq(Dish::getStatus, DishConst.STATUS_ON)) > 0;
    }

    @Override
    public Map<Long, String> mapNameByIds(Collection<Long> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>(dishIds.size());
        // 只取 id/name 两列，避免拉取整行（含 images 等大字段）；不过滤上架态（口径见接口注释）
        dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                        .select(Dish::getId, Dish::getName)
                        .in(Dish::getId, dishIds))
                .forEach(d -> map.put(d.getId(), d.getName()));
        return map;
    }

    /**
     * 纠错采纳写回（dish 域写契约，由 {@code CorrectionServiceImpl} 调用）：
     * 可空快照字段（name/price/stallId）不覆盖既有值（MyBatis-Plus NOT_NULL 策略跳过 null），
     * 保护「菜品首图必填」等既有不变量；images 的 JSON 序列化形态属 dish 落库口径，故收在本域。
     * <p>
     * <b>属性必须「按维度合并」</b>（口径见 docs/func/web/B-UGC治理/B4-菜品问题反馈管理.md 与
     * docs/schema/dish_correction.md）：纠错快照只含<b>改动维度</b>，整体覆盖会静默抹掉其余维度；
     * 用户提交空数组表示「清空该维度」⇒ 删除该键，不落空数组。
     * <p>
     * <b>images 入库前还原为相对路径</b>：快照存的是绝对地址，而 dish.images 是相对路径列。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean applyCorrection(DishCorrectionCmd cmd) {
        // 合并需要「现有属性」作为基线；顺带用「行不存在」实现并发删除兜底
        Dish existing = dishMapper.selectById(cmd.getDishId());
        if (existing == null) {
            return false;
        }
        Dish update = new Dish();
        update.setId(cmd.getDishId());
        update.setName(cmd.getName());
        update.setPrice(cmd.getPrice());
        update.setStallId(cmd.getStallId());
        if (cmd.getAttributes() != null && !cmd.getAttributes().isEmpty()) {
            update.setAttributes(JsonMapUtil.toJson(mergeAttributes(existing.getAttributes(), cmd.getAttributes())));
        }
        if (cmd.getImages() != null && !cmd.getImages().isEmpty()) {
            update.setImages(JsonListUtil.toJson(imageUrlUtil.toRelativePaths(cmd.getImages())));
        }
        boolean updated = dishMapper.updateById(update) > 0;
        if (updated) {
            // 纠错采纳改写 dish.attributes → 候选值集合同步失效（管理端与端上编辑弹层下次即见新值）
            attributeCatalog.invalidateCandidates();
        }
        return updated;
    }

    /**
     * 纠错采纳的属性合并：<b>以菜品现有 attributes 为基线</b>，只覆盖本次采纳的维度。
     *
     * @param existingJson 菜品当前属性 JSON（可为 null）
     * @param patch        本次采纳的维度补丁（键 = 维度 fieldKey）
     * @return 合并后的属性 JSON 入参对象
     */
    private Map<String, Object> mergeAttributes(String existingJson, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>(JsonMapUtil.parseObject(existingJson));
        // 纠错快照里的属性是**中文**（用户提交原样）⇒ 入库前解析为**取值 ID**
        // （命中即用 / 未命中自动登记新取值）—— 见 A4「灵活取值」与 B4「属性采纳的存储口径」。
        Map<String, Object> resolvedPatch = attributeAdminService.resolveForWrite(patch);
        patch.forEach((dimension, value) -> {
            // 空值 / 空数组 = 用户清空该维度 ⇒ 删除该键（resolveForWrite 会丢弃该键，故按 patch 判定）
            Object resolved = resolvedPatch.get(dimension);
            if (value == null || (value instanceof Collection<?> items && items.isEmpty())
                    || resolved == null) {
                merged.remove(dimension);
            } else {
                merged.put(dimension, resolved);
            }
        });
        return merged;
    }
}
