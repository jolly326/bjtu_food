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
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.dto.DishAttributeEditVO;
import com.bjtufood.dish.dto.DishAttributeItem;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishListItemVO;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.constant.DishConst;
import com.bjtufood.dish.dto.DishCorrectionCmd;
import com.bjtufood.dish.dto.GuessLikeVO;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.entity.DishAttributeDimension;
import com.bjtufood.dish.event.DishDeletedEvent;
import com.bjtufood.dish.mapper.DishAttributeDimensionMapper;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.dish.view.DishListQuery;
import com.bjtufood.dish.view.DishViewConst;
import com.bjtufood.dish.view.DishViewResolver;
import com.bjtufood.dish.view.DishViewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DishServiceImpl implements DishService {

    /**
     * 猜你喜欢返回条数。
     * <p>
     * 端上不写死条数、不截断、不排序，一律按返回渲染——**条数上限是数据源侧职责**。
     * 收为 6 的理由（见 docs/client/ui/client-搜索.md §1 第 4 条）：该接口当前是**纯随机**推送
     * （无推荐算法），8 条会占满发现态首屏（实测排成 3 行 chips），把「搜索记录」这个
     * 真正的个性化入口挤出可视区。
     */
    private static final int GUESS_LIKE_SIZE = 6;

    private final DishMapper dishMapper;
    private final StallService stallService;
    /**
     * 事件发布器：菜品删除后的评价级联清理改由 review 域监听器承接
     * （P0-1 写侧解耦，dish 域不再注入 ReviewMapper / ReviewService）。
     */
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUrlUtil imageUrlUtil;
    /** 描述属性维度字典（单表），数据驱动、免发版增维度；取值候选由 dish.attributes 全库去重得出 */
    private final DishAttributeDimensionMapper dishAttributeDimensionMapper;

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
        // 视图解析（白名单，PR-06）：空值 = 默认视图（「为你推荐」）；未登记的键 400 报错，不静默降级。
        // 筛选条件与排序口径均由视图 Kind 决定（见 DishViewResolver），API 层不感知 meal_type 等字段。
        DishListQuery query = DishViewResolver.resolve(req.getView(), req.getKeyword(), req.getSeed());
        if (query == null) {
            throw new BusinessException("筛选视图不合法：" + req.getView());
        }
        // 列表出参为 DishListItemVO（8 字段）：图片只下发首图 coverImage，由 enrichCoverImage 从 imageUrls 取首图
        return dishMapper.selectDishPage(new Page<>(req.getPage(), req.getPageSize()), query)
                .convert(this::enrichCoverImage);
    }

    @Override
    public List<DishViewVO> listDishViews() {
        // 空类过滤（§7.34）：只对「按大类取数」的视图生效——该大类当前无 status='on' 菜品即不下发，
        // 重新有菜自动出现；其余视图（「为你推荐」等聚合视角）恒下发。
        // 顺序 = DishViewConst.ALL 声明序（唯一顺序真源）；端上零文案、零拼接、零兜底项。
        Set<String> inStock = Set.copyOf(dishMapper.selectInStockMealTypes());
        return DishViewConst.ALL.stream()
                .filter(v -> v.kind() != DishViewConst.Kind.MEAL_TYPE || inStock.contains(v.param()))
                .map(v -> new DishViewVO(v.key(), v.label()))
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
        // 不存在与已下架同款处理（与详情口径一致）
        if (!existsOnSale(dishId)) {
            throw new BusinessException(4001, "菜品不存在");
        }
        Map<String, Object> raw = JsonMapUtil.parseObject(dishMapper.selectAttributesJson(dishId));
        if (raw.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> candidates = candidateValuesByFieldKey();
        return loadDimensions().stream()
                .filter(dim -> raw.containsKey(dim.getFieldKey()))
                .map(dim -> new DishAttributeEditVO(
                        dim.getFieldKey(),
                        dim.getValueType(),
                        candidates.getOrDefault(dim.getFieldKey(), List.of())))
                .toList();
    }

    /** 维度字典（按 order 升序） */
    private List<DishAttributeDimension> loadDimensions() {
        return dishAttributeDimensionMapper.selectList(
                new LambdaQueryWrapper<DishAttributeDimension>()
                        .orderByAsc(DishAttributeDimension::getOrder));
    }

    /**
     * 编辑候选值（数据驱动）：扫描全库在售菜品的 {@code dish.attributes}，按维度 {@code fieldKey}
     * 汇总「已用中文值」并按使用频次倒序去重——<b>无独立取值字典表，加值零登记</b>。
     */
    private Map<String, List<String>> candidateValuesByFieldKey() {
        Map<String, Map<String, Integer>> counter = new HashMap<>();
        for (String json : dishMapper.selectAttributesJsonOnSale()) {
            JsonMapUtil.parseObject(json).forEach((key, val) -> {
                Map<String, Integer> perValue = counter.computeIfAbsent(key, k -> new HashMap<>());
                if (val instanceof List<?> list) {
                    for (Object v : list) {
                        if (v != null) {
                            String s = String.valueOf(v).trim();
                            if (!s.isEmpty()) {
                                perValue.merge(s, 1, Integer::sum);
                            }
                        }
                    }
                } else if (val != null) {
                    String s = String.valueOf(val).trim();
                    if (!s.isEmpty()) {
                        perValue.merge(s, 1, Integer::sum);
                    }
                }
            });
        }
        Map<String, List<String>> result = new HashMap<>(counter.size());
        counter.forEach((key, perValue) -> result.put(key, perValue.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList()));
        return result;
    }

    /**
     * 把 {@code dish.attributes} 的 JSON 原文整理为出参 {@code attributes[]}（值即中文）。
     * <p>
     * 自描述：只含该菜品实际拥有的维度，按维度 {@code order} 升序；某维度无值则不出现、不占位。
     */
    private List<DishAttributeItem> buildAttributeItems(String attributesJson,
                                                        List<DishAttributeDimension> dimensions) {
        Map<String, Object> raw = JsonMapUtil.parseObject(attributesJson);
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
     * 计数口径（PV）：本方法**成功取到详情后**执行 {@code view_count + 1}（SQL 原子自增，
     * 避免读-改-写丢计数）。菜品不存在 / 已下架（vo == null）抛 {@code BusinessException(4001)}，不计数。
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
        // 并发安全：原子自增（UPDATE ... SET view_count = view_count + 1）；
        // vo 已确认存在，affected 恒为 1（若为 0 属并发删除，视同不存在）
        int affected = dishMapper.increaseViewCount(id);
        if (affected == 0) {
            throw new BusinessException(4001, "菜品不存在");
        }
        // 多图 → 绝对 URL（图片列已由 TypeHandler 直出为 List）
        enrichImages(vo);

        // 描述属性：JSON 原文即中文值 → 展示项（R4，端上零翻译）
        vo.setAttributes(buildAttributeItems(vo.getAttributesJson(), loadDimensions()));

        // avgRating 恒读缓存列 dish.avg_rating（零评价为 NULL → 出参 null），不做实时聚合。

        // hasReviewed（当前用户是否已评价）已于下线（三端零消费，连带删除字段与取值查询）。
        // 注：详情出参仍无任何登录态字段；原 userId 入参与 view_log 浏览日志写入已随该链整表退役移除。
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
     * 真随机分支（未传 seed）本就与缓存语义冲突，更不能缓存。
     */
    @Override
    public List<GuessLikeVO> guessLike(String seed) {
        return dishMapper.selectGuessLike(GUESS_LIKE_SIZE, seed);
    }

    @Override
    @Deprecated(since = "2026-09", forRemoval = true)
    public IPage<DishAdminVO> listAllForAdmin(int page, int pageSize) {
        // ⚠️ 冻结：管理端（Web 后台）方法，待后期整体重构时移除。本期保留可编译、保留功能，不删除。
        // 分页上限统一由 PageUtil 约束，避免一次性全表加载
        int[] norm = PageUtil.normalize(page, pageSize);
        page = norm[0];
        pageSize = norm[1];
        IPage<DishAdminVO> result = dishMapper.selectAllForAdmin(new Page<>(page, pageSize));
        return PageUtil.toVoPage(result, recs -> recs.stream().map(this::enrichImages).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Deprecated(since = "2026-09", forRemoval = true)
    public void addDish(DishAdminReq req) {
        // ⚠️ 冻结：管理端（Web 后台）方法，待后期整体重构时移除。本期保留可编译、保留功能，不删除。
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
        // avg_rating 保持 NULL（零评价 → 公开出参 avgRating = null，端上按「暂无评分」呈现）
        dish.setRatingCount(0);
        dish.setViewCount(0);
        if (!StringUtils.hasText(dish.getStatus())) {
            dish.setStatus(DishConst.STATUS_ON);
        }
        // 注：菜品审核语义已整体退役（dish.audit_status 列与写入同批移除，阶段4）——
        // 管理员即权威，录入/编辑后菜品直接生效，「落库默认值导致新菜不可见」的顾虑不再存在。
        dishMapper.insert(dish);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Deprecated(since = "2026-09", forRemoval = true)
    public void updateDish(Long id, DishAdminReq req) {
        // ⚠️ 冻结：管理端（Web 后台）方法，待后期整体重构时移除。本期保留可编译、保留功能，不删除。
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
        // 同上：审核语义退役后编辑路径不再回写审核态，
        // 「改了信息反而从端上消失」的隐患随 audit_status 列下线一并消除。
        dishMapper.updateById(dish);
        // 契约约定：null/0 表示清空可空的原价（applyReq 已把 0 归一为 null 并写回实体）；
        // updateById 默认 NOT_NULL 策略不落 null，需显式置空
        boolean clearOriginalPrice = dish.getOriginalPrice() == null;
        if (clearOriginalPrice) {
            LambdaUpdateWrapper<Dish> clearWrapper = new LambdaUpdateWrapper<Dish>().eq(Dish::getId, id);
            clearWrapper.set(Dish::getOriginalPrice, null);
            dishMapper.update(null, clearWrapper);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Deprecated(since = "2026-09", forRemoval = true)
    public void deleteDish(Long id) {
        // ⚠️ 冻结：管理端（Web 后台）方法，待后期整体重构时移除。本期保留可编译、保留功能，不删除。
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        // 级联清理该菜品下的全部评价（BE-108）：改为发布领域事件，由 review 域监听器删除评价，
        // dish 域不再持有 review 表知识（P0-1）。同步监听 → 仍在本次事务内执行，失败一并回滚。
        eventPublisher.publishEvent(new DishDeletedEvent(id));
        dishMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // 评分重算由 RatingUpdateListener 在事务 AFTER_COMMIT 后异步触发，故写库与重算之间无竞态窗口
    // （原注释关于 @CacheEvict 失效时序的说明已随 缓存设施整包退役删除）
    public void recalcAvgRating(Long dishId) {
        // 并发安全：子查询 AVG/COUNT 整体写回，避免全量查询后回写丢数据。
        // 计入口径（Q-110 / 归一）：仅 is_hidden=0 的评价计入（sec_state 已全链退役，
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
     * 空值语义名称判断（§7.23 第 1 条：「其他/其它/无/未知」视为未填，回退 stallId 逻辑）。
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
     * {@code originalPrice} 为可空原价；不再存在 promo_price 第三价格字段。
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

        // 描述属性（动态属性模型）：JSON 对象，键 = 维度 fieldKey；null/空 → 列置 NULL
        dish.setAttributes(JsonMapUtil.toJson(req.getAttributes()));

        // 菜品大类（入库字段，§7.34）：值域 = DishViewConst 派生的大类视图集合（单一真源）；
        // 白名单校验（PR-06，非法值 400）；null=不修改
        // （编辑路径 MyBatis-Plus NOT_NULL 策略跳过 null 字段，「仅传 status 的行内部分更新」不会误清大类）
        if (StringUtils.hasText(req.getMealType()) && !DishViewConst.mealTypeValues().contains(req.getMealType())) {
            throw new BusinessException("菜品大类不合法：" + req.getMealType());
        }
        dish.setMealType(req.getMealType());

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
     * 无图时为空串（列表不再下发图片数组，故只取首图、不做多图回填）。
     */
    private DishListItemVO enrichCoverImage(DishListItemVO vo) {
        if (vo == null) {
            return null;
        }
        List<String> urls = toAbsoluteImages(vo.getImageUrls());
        vo.setCoverImage(urls.isEmpty() ? "" : urls.get(0));
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
     * 纠错采纳写回（原实现位于 {@code CorrectionServiceImpl.applyAdoption}，逻辑逐字保留）：
     * 可空快照字段（flavorTags/ingredients/images）不覆盖既有值（MyBatis-Plus NOT_NULL 策略跳过 null），
     * 保护「菜品首图必填」等既有不变量；images 的 JSON 序列化形态属 dish 落库口径，故收在本域。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean applyCorrection(DishCorrectionCmd cmd) {
        Dish update = new Dish();
        update.setId(cmd.getDishId());
        update.setName(cmd.getName());
        update.setPrice(cmd.getPrice());
        update.setStallId(cmd.getStallId());
        if (cmd.getAttributes() != null && !cmd.getAttributes().isEmpty()) {
            update.setAttributes(JsonMapUtil.toJson(cmd.getAttributes()));
        }
        if (cmd.getImages() != null && !cmd.getImages().isEmpty()) {
            update.setImages(JsonListUtil.toJson(cmd.getImages()));
        }
        return dishMapper.updateById(update) > 0;
    }
}
