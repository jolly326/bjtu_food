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
import com.bjtufood.dish.constant.DishAttributeConst;
import com.bjtufood.dish.constant.MealTypeConst;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.dto.DishAttributeVO;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishListItemVO;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.constant.DishConst;
import com.bjtufood.dish.dto.GuessLikeVO;
import com.bjtufood.dish.dto.MealTypeVO;
import com.bjtufood.dish.dto.RatingDistributionVO;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DishServiceImpl implements DishService {

    /**
     * 猜你喜欢返回条数（2026-09-22 change search-page-refresh；2026-09-23 由 8 收为 6）。
     * <p>
     * 端上不写死条数、不截断、不排序，一律按返回渲染——**条数上限是数据源侧职责**。
     * 收为 6 的理由（见 docs/ui/client-搜索.md §1 第 4 条）：该接口当前是**纯随机**推送
     * （无推荐算法），8 条会占满发现态首屏（实测排成 3 行 chips），把「搜索记录」这个
     * 真正的个性化入口挤出可视区。
     */
    private static final int GUESS_LIKE_SIZE = 6;

    private final DishMapper dishMapper;
    private final StallService stallService;
    private final ReviewMapper reviewMapper;
    private final ImageUrlUtil imageUrlUtil;

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
        // 菜品大类白名单校验（PR-06 / §7.34）：非法值 400 报错，不静默降级；空值=「全部」，不进 SQL 条件
        if (StringUtils.hasText(req.getMealType()) && !MealTypeConst.isValid(req.getMealType())) {
            throw new BusinessException("菜品大类不合法：" + req.getMealType());
        }
        // 列表出参为 DishListItemVO（8 字段）：图片只下发首图 coverImage，由 enrichCoverImage 从 imageUrls 取首图
        return dishMapper.selectDishPage(new Page<>(req.getPage(), req.getPageSize()), req)
                .convert(this::enrichCoverImage);
    }

    @Override
    public List<MealTypeVO> listMealTypes() {
        // 空类过滤（§7.34）：常量清单（唯一真源）∩「当前有在售菜品」的大类集合——
        // 某类暂时没有 status='on' 的菜品即不下发，重新有菜自动出现；顺序 = 常量声明序（order 升序）
        Set<String> inStock = Set.copyOf(dishMapper.selectInStockMealTypes());
        return MealTypeConst.ALL.stream()
                .filter(mt -> inStock.contains(mt.value()))
                .map(mt -> new MealTypeVO(mt.value(), mt.label(), mt.order()))
                .collect(Collectors.toList());
    }

    /**
     * 菜品描述四维字典（2026-09-23 §7.40 R4 / R13）：{@code GET /dishes/attributes} 出参。
     * <p>
     * 内容取自 {@link DishAttributeConst}（唯一真源），**不查库、不做在售过滤** ——
     * 四维是描述属性，管理端录入表单需要完整选项（与 meal-types「只下发有在售菜品的大类」策略不同）。
     */
    @Override
    public List<DishAttributeVO> listAttributes() {
        return DishAttributeConst.ALL.stream()
                .map(a -> new DishAttributeVO(a.field(), a.value(), a.label(), a.order()))
                .collect(Collectors.toList());
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
        // 从 images_json 解析 images（避免二次查数据库）
        enrichImages(vo);

        // 查询评分分布（SQL 侧已按 rating DESC；再经 fillRatingDistribution 补齐为恒 5 项、顺序 5→1）
        List<RatingDistributionVO> distribution = dishMapper.selectRatingDistribution(id);
        List<RatingDistributionVO> filledDistribution = fillRatingDistribution(distribution);
        vo.setRatingDistribution(filledDistribution);

        // 评分摘要三数同源（2026-09-23 R1）：合计与均分改由**同一次实时聚合**产出，
        // 覆盖 dish.rating_count / dish.avg_rating 缓存列 —— 避免缓存漂移时卡内数字自相矛盾
        applyRatingSummaryFromDistribution(vo, filledDistribution);

        // hasReviewed（当前用户是否已评价）已于 2026-09-15 下线（三端零消费，连带删除字段与取值查询）。
        // 注：详情出参仍无任何登录态字段；userId 入参保留，仅用于本方法前半段的 view_log 浏览日志写入。
        return vo;
    }

    /**
     * 猜你喜欢：每次请求**随机**取在售菜品名，故**不加缓存**
     * （响应缓存会让「每次随机」退化为「全站同一份」，change search-page-refresh）。
     */
    @Override
    public List<GuessLikeVO> guessLike() {
        return dishMapper.selectGuessLike(GUESS_LIKE_SIZE);
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
        if (clearOriginalPrice) {
            LambdaUpdateWrapper<Dish> clearWrapper = new LambdaUpdateWrapper<Dish>().eq(Dish::getId, id);
            clearWrapper.set(Dish::getOriginalPrice, null);
            dishMapper.update(null, clearWrapper);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDish(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        // 级联清理该菜品下的全部评价（BE-108）
        reviewMapper.delete(new LambdaQueryWrapper<Review>().eq(Review::getDishId, id));
        dishMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // 评分重算由 RatingUpdateListener 在事务 AFTER_COMMIT 后异步触发，故写库与重算之间无竞态窗口
    // （原注释关于 @CacheEvict 失效时序的说明已随 2026-09-22 缓存设施整包退役删除）
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
     * 价格口径（2026-09-20 拍板 D2）：唯一数据源为 {@code price}（现价，已含折扣），
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

        // 描述四维（§7.28 定型）：荤素 / 主料 / 口味 / 冷热（原辣度 spice_level、风味 region 已下线）
        dish.setDietType(req.getDietType());
        dish.setIngredients(req.getIngredients());
        dish.setFlavorTags(req.getFlavorTags());
        dish.setServeTemp(req.getServeTemp());

        // 菜品大类（§7.34）：白名单校验（PR-06，非法值 400）；null=不修改
        // （编辑路径 MyBatis-Plus NOT_NULL 策略跳过 null 字段，「仅传 status 的行内部分更新」不会误清大类）
        if (StringUtils.hasText(req.getMealType()) && !MealTypeConst.isValid(req.getMealType())) {
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
     * 图片相对路径 → 绝对 URL（2026-09-23 R5）。
     * <p>
     * 「JSON 串 ↔ List」的转换已下沉到持久层（{@code StringListTypeHandler}），本方法只负责
     * **业务转换**（相对路径 → 可访问绝对 URL）；空值归一为空列表。
     */
    private List<String> toAbsoluteImages(List<String> images) {
        return images == null || images.isEmpty() ? List.of() : imageUrlUtil.toAbsoluteUrls(images);
    }

    /**
     * 列表行封面图（2026-09-22 D 项拆分）：取 {@code imageUrls} 的**首图**填入 {@code coverImage}；
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

    /**
     * 评分摘要三数同源（2026-09-23 change {@code dish-detail-contract-hardening} R1）。
     * <p>
     * 把详情页的「N 人评分」与「均分」改由**与评分分布同一次实时聚合**的结果产出：
     * 合计 = Σcount，均分 = Σ(star × count) / 合计。三者同源同刻，卡内数字恒自洽 ——
     * 不再出现「各星占比之和 100%，但同卡『N 人评分』是另一个数」这类自相矛盾
     * （缓存列 {@code dish.rating_count} / {@code dish.avg_rating} 由异步聚合刷新，存在漂移窗口）。
     * <p>
     * <b>取舍</b>：两个缓存列**保留**（列表页排序与卡片展示继续使用），仅详情页的评分摘要改用实时值 ——
     * 「同一卡片内自洽」优先于「跨页面一致」，后者在缓存重算后自然收敛（design D6）。
     * 入参应为已补齐为 5 项的分布（缺失星级 count 为 0，不影响合计与加权和）。
     */
    private void applyRatingSummaryFromDistribution(DishDetailVO vo, List<RatingDistributionVO> distribution) {
        long total = 0L;
        long weighted = 0L;
        if (distribution != null) {
            for (RatingDistributionVO item : distribution) {
                long count = item.getCount() == null ? 0L : item.getCount();
                int star = item.getStar() == null ? 0 : item.getStar();
                total += count;
                weighted += count * star;
            }
        }
        vo.setRatingCount((int) total);
        // 保留一位小数，与既有 avgRating 展示口径一致；无评价时为 null（端上不渲染评分卡）
        vo.setAvgRating(total == 0L
                ? null
                : BigDecimal.valueOf(weighted).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP));
    }
}
