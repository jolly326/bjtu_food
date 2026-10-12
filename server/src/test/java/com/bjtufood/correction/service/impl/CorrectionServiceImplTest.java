package com.bjtufood.correction.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.correction.constant.CorrectionConst;
import com.bjtufood.correction.dto.CorrectionAdminVO;
import com.bjtufood.correction.dto.CorrectionDetailVO;
import com.bjtufood.correction.dto.DishCorrectionDifferenceVO;
import com.bjtufood.correction.dto.DishCorrectionHandleReq;
import com.bjtufood.correction.dto.DishCorrectionReq;
import com.bjtufood.correction.entity.Correction;
import com.bjtufood.correction.mapper.CorrectionMapper;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.service.DishAttributeAdminService;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.moderation.constant.ModerationConst;
import com.bjtufood.moderation.dto.ModerationHandleReq;
import com.bjtufood.moderation.dto.ModerationTaskVO;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
import com.bjtufood.moderation.service.ModerationTaskService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CorrectionServiceImpl} 单元测试。
 * <p>
 * 目的：把提交 / 处置的可见行为钉死。聚焦四类退化后即为安全 / 数据问题的逻辑：
 * <ol>
 *   <li><b>局部提交（patch）语义</b>：未传字段保持未改动（落库 NULL），空请求体 400「未提交任何改动」；</li>
 *   <li><b>机审合并送检</b>：多文本字段须合并为<b>单次</b> {@code checkText}（按调用计费）；
 *       纯 price / images 改动<b>不送检</b>；</li>
 *   <li><b>处置前置校验</b>：处置任务非 pending 一律 400（重复点击 = 已处理），反馈不存在 4001；</li>
 *   <li><b>楼层纠错</b>：floor 传入即非空 / ∈ 楼层字典（值即汉字）/ ≤16 字校验；仅改楼层也算「有改动」。</li>
 * </ol>
 * <b>标记已处理不改写菜品</b>：{@code handle} 只归档 {@code outcome=handled}（已受理）+ 回执，
 * 菜品资料修改 / {@code gone} 可逆下架由管理员在菜品管理手动执行，不在本域。
 * <p>
 * <b>处置归档与回执</b>（状态 / 结论 / 文案 / 投递）已外置到处置任务服务，
 * 由 {@code ModerationTaskServiceImplTest} 逐条断言；本类只断言「委派时的入参正确」。
 * <p>
 * 被测类为纯 POJO：{@code @Transactional} 依赖 Spring 代理，单测中不生效，断言的是方法体内业务逻辑。
 */
class CorrectionServiceImplTest {

    /** 纯 Mockito 无 Spring 上下文，MyBatis-Plus 的 lambda 缓存需显式初始化 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), CorrectionServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, Correction.class);
    }

    private final CorrectionMapper correctionMapper = mock(CorrectionMapper.class);
    private final DishService dishService = mock(DishService.class);
    private final StallService stallService = mock(StallService.class);
    private final UserService userService = mock(UserService.class);
    private final LocalSensitiveFilter localSensitiveFilter = mock(LocalSensitiveFilter.class);
    private final ContentSecurityService contentSecurityService = mock(ContentSecurityService.class);
    private final ImageUrlUtil imageUrlUtil = mock(ImageUrlUtil.class);
    private final ModerationTaskService moderationTaskService = mock(ModerationTaskService.class);

    /**
     * 默认桩（在用例体之前建立，故用例内的重桩可覆盖）：
     * <ul>
     *   <li>逐项采纳的前置是「采纳项此刻仍有差异」⇒ 详情对照需一份可取的**实时菜品**，
     *       默认给「处处不同于快照」的实时值（各用例只关心档口 / 楼层分支，不关心具体差异内容）；</li>
     *   <li>处置前置：默认处置任务仍在待处置状态（用例按需覆盖为已处置 / 缺失）。</li>
     * </ul>
     */
    @org.junit.jupiter.api.BeforeEach
    void setUpDefaultStubs() {
        when(dishService.getForAdmin(anyLong())).thenReturn(liveDish());
        when(moderationTaskService.findByTypeAndId(eq(ModerationConst.TYPE_CORRECTION), anyLong()))
                .thenReturn(pendingTask());
    }

    /**
     * 构造器参数顺序须与 {@code CorrectionServiceImpl} 的 final 字段声明顺序逐字一致（@RequiredArgsConstructor）。
     * <p>
     * 落库 Bean 用**真实实现**包裹 mock 的 mapper / 任务服务：事务边界收窄（机审移出事务）后，
     * 本类断言仍原样落在 {@code correctionMapper.insert} 与 {@code moderationTaskService.create} 上。
     */
    private CorrectionServiceImpl service() {
        return new CorrectionServiceImpl(correctionMapper, new CorrectionPersister(correctionMapper, moderationTaskService),
                dishService, mock(DishAttributeAdminService.class), stallService, userService, localSensitiveFilter,
                contentSecurityService, imageUrlUtil, moderationTaskService);
    }

    private static ModerationTaskVO pendingTask() {
        ModerationTaskVO vo = new ModerationTaskVO();
        vo.setStatus(ModerationConst.STATUS_PENDING);
        return vo;
    }

    /** 实时菜品（处处不同于 {@link #pendingCorrection} 的快照，使 name/canteenName/stallName 都是「仍有差异」项） */
    private static DishAdminVO liveDish() {
        DishAdminVO live = new DishAdminVO();
        live.setId(3L);
        live.setStallId(10L);
        live.setName("旧菜名");
        live.setCanteenName("旧食堂");
        live.setStallName("旧档口");
        live.setStatus("on");
        live.setPrice(1200);
        live.setImages(List.of());
        live.setAttributes(Map.of());
        return live;
    }

    /** 标记已处理请求：带可选回执标题 / 处理附注 */
    private static DishCorrectionHandleReq handleReq(String title, String content) {
        DishCorrectionHandleReq req = new DishCorrectionHandleReq();
        req.setReplyTitle(title);
        req.setReplyContent(content);
        return req;
    }

    private DishCorrectionReq req() {
        return new DishCorrectionReq();
    }

    private Correction pendingCorrection(Long userId) {
        Correction c = new Correction();
        c.setId(9L);
        c.setDishId(3L);
        c.setUserId(userId);
        c.setName("牛肉拉面");
        c.setCanteenName("清真食堂");
        c.setStallName("清真面档");
        return c;
    }

    // ==================== submit：存在性 / patch 语义 / 字段校验 ====================

    @Test
    @DisplayName("submit：菜品不存在或已下架 → 4001 且不落库（公开口径下「下架」等价于「不存在」）")
    void submit_dishNotOnSale_4001AndNotPersisted() {
        when(dishService.existsOnSale(anyLong())).thenReturn(false);
        DishCorrectionReq req = req();
        req.setName("牛肉拉面");

        assertThatThrownBy(() -> service().submit(1L, 3L, req))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(correctionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submit：空请求体（无任何改动项）→ 400「未提交任何改动」且不落库")
    void submit_emptyPatch_400AndNotPersisted() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);

        assertThatThrownBy(() -> service().submit(1L, 3L, req()))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(correctionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submit：名称超长 → 400 且不落库")
    void submit_nameTooLong_400() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        DishCorrectionReq req = req();
        req.setName("面".repeat(CorrectionConst.NAME_MAX_LENGTH + 1));

        assertThatThrownBy(() -> service().submit(1L, 3L, req))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(correctionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submit：价格非正整数（0 / 负数）→ 400 且不落库")
    void submit_priceNotPositive_400() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        for (int bad : new int[]{0, -1}) {
            DishCorrectionReq req = req();
            req.setPrice(bad);
            assertThatThrownBy(() -> service().submit(1L, 3L, req))
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        }
        verify(correctionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submit：配图超过上限或非 COS 合法地址 → 400 且不落库")
    void submit_imagesInvalid_400() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        when(imageUrlUtil.isValidCosUgcUrl(any())).thenReturn(true);

        // 超上限
        DishCorrectionReq many = req();
        many.setPrice(100);
        many.setImages(java.util.stream.Stream.generate(() -> "https://x/a.jpg")
                .limit(CorrectionConst.IMAGE_MAX + 1).toList());
        assertThatThrownBy(() -> service().submit(1L, 3L, many))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        // 地址非法
        when(imageUrlUtil.isValidCosUgcUrl(any())).thenReturn(false);
        DishCorrectionReq bad = req();
        bad.setPrice(100);
        bad.setImages(List.of("http://evil.com/a.jpg"));
        assertThatThrownBy(() -> service().submit(1L, 3L, bad))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(correctionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submit：名称命中本地敏感词 → 400 且不落库（写回字段不放行替换版）")
    void submit_nameSensitive_400() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        when(localSensitiveFilter.containsSensitive(any())).thenReturn(true);
        DishCorrectionReq req = req();
        req.setName("违规名称");

        assertThatThrownBy(() -> service().submit(1L, 3L, req))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(correctionMapper, never()).insert(any());
    }

    // ==================== submit：机审合并送检 ====================

    @Test
    @DisplayName("submit：多文本字段合并为**单次**机审（按调用计费，不得逐字段送检）")
    void submit_multipleTextFields_singleSecCheckCall() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        UserAuthContextVO user = new UserAuthContextVO();
        user.setOpenid("oXxx");
        when(userService.getAuthContext(7L)).thenReturn(user);

        DishCorrectionReq req = req();
        req.setName("牛肉拉面");
        req.setCanteenName("清真食堂");
        req.setStallName("清真面档");
        service().submit(7L, 3L, req);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(contentSecurityService).checkText(org.mockito.ArgumentMatchers.eq("oXxx"), text.capture(),
                org.mockito.ArgumentMatchers.eq(2));
        // 三个文本字段以换行拼接成一条（直接相连会让边界词偶然拼出新词 → 误判）
        assertThat(text.getValue()).isEqualTo("牛肉拉面\n清真食堂\n清真面档");
    }

    @Test
    @DisplayName("submit：仅改 price / 配图（无文本）→ 不发起机审，且落库两行（纠错行 + 待处置任务行）")
    void submit_priceOnly_noSecCheckAndPreCreatesTask() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        when(imageUrlUtil.isValidCosUgcUrl(any())).thenReturn(true);
        when(correctionMapper.insert(any())).thenAnswer(inv -> {
            ((Correction) inv.getArgument(0)).setId(21L);
            return 1;
        });
        DishCorrectionReq req = req();
        req.setPrice(1200);

        service().submit(7L, 3L, req);

        verify(contentSecurityService, never()).checkText(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        ArgumentCaptor<Correction> captor = ArgumentCaptor.forClass(Correction.class);
        verify(correctionMapper).insert(captor.capture());
        // patch 语义：未传字段保持 NULL（不覆盖菜品既有值）
        assertThat(captor.getValue().getName()).isNull();
        assertThat(captor.getValue().getCanteenName()).isNull();
        assertThat(captor.getValue().getStallName()).isNull();
        assertThat(captor.getValue().getPrice()).isEqualTo(1200);
        // 「未处置」是显式行：与纠错行同一事务预建
        verify(moderationTaskService).create(ModerationConst.TYPE_CORRECTION, 21L);
    }

    @Test
    @DisplayName("submit：空 attributes 视为未提供（无改动），不参与「未提交任何改动」判定")
    void submit_emptyAttributes_treatedAsAbsent() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        DishCorrectionReq req = req();
        req.setAttributes(Map.of());

        assertThatThrownBy(() -> service().submit(1L, 3L, req))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(correctionMapper, never()).insert(any());
    }

    // ==================== 楼层纠错：提交校验 / 空改动判据 / 管理端装配 ====================

    @Test
    @DisplayName("submit：楼层传入为空或纯空白 → 400「楼层不能为空」且不落库（不静默降级为空改动）")
    void submit_blankFloor_400() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        for (String blank : new String[]{"", "   "}) {
            DishCorrectionReq req = req();
            req.setFloor(blank);
            assertThatThrownBy(() -> service().submit(1L, 3L, req))
                    .satisfies(ex -> {
                        assertThat(((BusinessException) ex).getCode()).isEqualTo(400);
                        assertThat(ex.getMessage()).isEqualTo("楼层不能为空");
                    });
        }
        verify(correctionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submit：楼层超长（>16 字，与 stall.floor VARCHAR(16) 对齐）→ 400「楼层超长」且不落库")
    void submit_floorTooLong_400() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        DishCorrectionReq req = req();
        req.setFloor("层".repeat(CorrectionConst.FLOOR_MAX_LENGTH + 1));

        assertThatThrownBy(() -> service().submit(1L, 3L, req))
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).getCode()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo("楼层超长");
                });
        verify(correctionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submit：楼层严格命中字典（值即汉字）→ 落库该汉字；字典外值（2F / B1 / 三楼）→ 400")
    void submit_floorMustHitDict() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        // 命中：trim 后落库值即提交的汉字
        DishCorrectionReq ok = req();
        ok.setFloor("  二层  ");
        service().submit(1L, 3L, ok);
        ArgumentCaptor<Correction> captor = ArgumentCaptor.forClass(Correction.class);
        verify(correctionMapper).insert(captor.capture());
        assertThat(captor.getValue().getFloor()).isEqualTo("二层");

        // 字典外：机器值 / 近似汉字 / 自由文本全部拒绝 —— 避免「落库成功但采纳写回 stall.floor 时 400」
        for (String bad : new String[]{"2F", "B1", "三楼", "二层东侧"}) {
            DishCorrectionReq req = req();
            req.setFloor(bad);
            assertThatThrownBy(() -> service().submit(1L, 3L, req))
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        }
    }

    @Test
    @DisplayName("submit：**仅改楼层**（其余改动项未传）算「有改动」→ 落库 floor 快照，不得判 400「未提交任何改动」")
    void submit_floorOnly_persistsAsChange() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        DishCorrectionReq req = req();
        req.setFloor("  二层  ");   // 传入值 trim 后落库（字典值即汉字）

        service().submit(1L, 3L, req);

        ArgumentCaptor<Correction> captor = ArgumentCaptor.forClass(Correction.class);
        verify(correctionMapper).insert(captor.capture());
        Correction saved = captor.getValue();
        assertThat(saved.getFloor()).isEqualTo("二层");
        assertThat(saved.getDishId()).isEqualTo(3L);
        assertThat(saved.getUserId()).isEqualTo(1L);
        // 其余改动项保持 NULL（patch 语义：未改动不落库、采纳时不覆盖既有值）
        assertThat(saved.getName()).isNull();
        assertThat(saved.getPrice()).isNull();
        assertThat(saved.getCanteenName()).isNull();
        assertThat(saved.getStallName()).isNull();
        assertThat(saved.getAttributes()).isNull();
    }

    @Test
    @DisplayName("submit：楼层为受控字典值 → 与其他文本字段合并为**单次**机审（采纳后进入公开展示）")
    void submit_floorIncludedInSingleSecCheckCall() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        UserAuthContextVO user = new UserAuthContextVO();
        user.setOpenid("oXxx");
        when(userService.getAuthContext(7L)).thenReturn(user);

        DishCorrectionReq req = req();
        req.setStallName("清真面档");
        req.setFloor("二层");
        service().submit(7L, 3L, req);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(contentSecurityService).checkText(org.mockito.ArgumentMatchers.eq("oXxx"), text.capture(),
                org.mockito.ArgumentMatchers.eq(2));
        // 楼层与其它文本字段同批送检：漏检即可把违规文本写进公开可见的 stall.floor
        assertThat(text.getValue()).isEqualTo("清真面档\n二层");
    }

    @Test
    @DisplayName("管理端列表：floor 装配进 VO（提交的楼层快照对后台可见，未改动为 null）")
    void listForAdmin_assemblesFloor() {
        Correction withFloor = pendingCorrection(1L);
        withFloor.setFloor("二层");
        Correction withoutFloor = pendingCorrection(2L);
        Page<Correction> page = new Page<>(1, 10);
        page.setRecords(List.of(withFloor, withoutFloor));
        page.setTotal(2);
        when(correctionMapper.selectPage(any(), any())).thenReturn(page);
        when(userService.mapNameByIds(any())).thenReturn(Map.of(1L, "交大干饭王", 2L, "食堂常客"));
        when(dishService.mapNameByIds(any())).thenReturn(Map.of(3L, "牛肉拉面"));

        List<CorrectionAdminVO> records = service().listForAdmin(null, null, null, 1, 10).getRecords();

        assertThat(records).hasSize(2);
        assertThat(records.get(0).getFloor()).isEqualTo("二层");
        assertThat(records.get(1).getFloor()).isNull();
    }

    // ==================== 标记已处理：前置校验 + 只归档不改菜品 ====================

    @Test
    @DisplayName("handle：反馈不存在 → 4001（目标态契约，见 docs/api/web/corrections.md）")
    void handle_notFound_4001() {
        when(correctionMapper.selectById(anyLong())).thenReturn(null);
        assertThatThrownBy(() -> service().handle(9L, null, 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
    }

    @Test
    @DisplayName("handle：处置任务缺失（数据不一致）→ 4001（不当成「已处理」误导管理员）")
    void handle_taskMissing_4001() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(moderationTaskService.findByTypeAndId(eq(ModerationConst.TYPE_CORRECTION), anyLong())).thenReturn(null);

        assertThatThrownBy(() -> service().handle(9L, handleReq(null, null), 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(moderationTaskService, never()).handle(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handle：已处置（非 pending）→ 400（幂等：重复点击读作「该反馈已处理」）")
    void handle_alreadyHandled_400() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        ModerationTaskVO handled = new ModerationTaskVO();
        handled.setStatus(ModerationConst.STATUS_HANDLED);
        when(moderationTaskService.findByTypeAndId(eq(ModerationConst.TYPE_CORRECTION), anyLong()))
                .thenReturn(handled);

        assertThatThrownBy(() -> service().handle(9L, handleReq(null, null), 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(moderationTaskService, never()).handle(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handle：field 型 → 归档 outcome=handled + 缺省文案，**绝不写回 dish / stall**")
    void handle_field_archivesWithoutWritingDish() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));

        service().handle(9L, null, 1L);

        verify(moderationTaskService).handle(ModerationConst.TYPE_CORRECTION, 9L, ModerationConst.OUTCOME_HANDLED,
                CorrectionConst.HANDLE_TITLE, CorrectionConst.HANDLE_REPLY, 1L, 1L);
        // 红线：标记已处理不改菜品（不写 dish 上下架、不写 stall 楼层）
        verify(dishService, never()).updateStatus(anyLong(), anyString());
        verify(stallService, never()).updateFloor(any(), any());
        // 提交行 append-only：处置不改写提交行
        verify(correctionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("handle：管理员自填回执标题 / 附注 → 原样下发（trim）")
    void handle_customTitleAndNote_win() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));

        service().handle(9L, handleReq(" 已核实 ", " 已按反馈更正 "), 3L);

        verify(moderationTaskService).handle(ModerationConst.TYPE_CORRECTION, 9L, ModerationConst.OUTCOME_HANDLED,
                "已核实", "已按反馈更正", 3L, 1L);
    }

    @Test
    @DisplayName("handle：gone 型 → 同样只归档（下架不在本端点），不触发 updateStatus")
    void handle_gone_archivesOnly() {
        Correction gone = pendingCorrection(1L);
        gone.setType(CorrectionConst.TYPE_GONE);
        when(correctionMapper.selectById(anyLong())).thenReturn(gone);

        service().handle(9L, null, 3L);

        verify(moderationTaskService).handle(ModerationConst.TYPE_CORRECTION, 9L, ModerationConst.OUTCOME_HANDLED,
                CorrectionConst.HANDLE_TITLE, CorrectionConst.HANDLE_REPLY, 3L, 1L);
        // gone 型下架由管理员在菜品管理手动执行，本端点不调 updateStatus
        verify(dishService, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    @DisplayName("getDetail：differences 只列「用户提交过的项」，oldValue 取实时值 + same 标记是否一致")
    void getDetail_listsOnlyRemainingDifferencesWithLiveValues() {
        Correction c = pendingCorrection(1L);
        c.setPrice(1600);   // 快照含价格改动
        when(correctionMapper.selectById(anyLong())).thenReturn(c);
        when(dishService.existsById(anyLong())).thenReturn(true);
        CorrectionServiceImpl svc = service();
        when(userService.mapNameByIds(any())).thenReturn(Map.of(1L, "交大干饭王"));

        CorrectionDetailVO vo = svc.getDetail(9L);

        // 菜品名与当前状态均取实时值（liveDish）
        assertThat(vo.getDishName()).isEqualTo("旧菜名");
        assertThat(vo.getDishStatus()).isEqualTo("on");
        assertThat(vo.getDifferences()).extracting(DishCorrectionDifferenceVO::getField)
                .containsExactlyInAnyOrder("name", "canteenName", "stallName", "price");
        // oldValue = 实时值（liveDish 的「旧菜名」），而非快照的「牛肉拉面」
        DishCorrectionDifferenceVO nameDiff = vo.getDifferences().stream()
                .filter(d -> "name".equals(d.getField())).findFirst().orElseThrow();
        assertThat(nameDiff.getOldValue()).isEqualTo("旧菜名");
        assertThat(nameDiff.getNewValue()).isEqualTo("牛肉拉面");
        // same = 提交值与当前实时值是否一致（此处不同 ⇒ false）
        assertThat(nameDiff.isSame()).isFalse();
    }

    @Test
    @DisplayName("getDetail：提交值与当前值一致的项 → same=true（只列用户提交过的项，不改写菜品）")
    void getDetail_sameValue_markedSame() {
        Correction c = pendingCorrection(1L);   // 快照 name=牛肉拉面
        when(correctionMapper.selectById(anyLong())).thenReturn(c);
        when(dishService.existsById(anyLong())).thenReturn(true);
        CorrectionServiceImpl svc = service();
        when(userService.mapNameByIds(any())).thenReturn(Map.of(1L, "交大干饭王"));
        // 实时菜品名称 = 快照名称 ⇒ 该项 same=true
        DishAdminVO same = liveDish();
        same.setName(c.getName());
        when(dishService.getForAdmin(anyLong())).thenReturn(same);

        CorrectionDetailVO vo = svc.getDetail(9L);

        DishCorrectionDifferenceVO nameDiff = vo.getDifferences().stream()
                .filter(d -> "name".equals(d.getField())).findFirst().orElseThrow();
        assertThat(nameDiff.isSame()).isTrue();
    }

    @Test
    @DisplayName("getDetail：目标菜品已物理删除 → 仍列用户提交过的项（oldValue 为 null，无从对照）")
    void getDetail_dishDeleted_listsSubmittedWithNullLive() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(false);
        CorrectionServiceImpl svc = service();

        CorrectionDetailVO vo = svc.getDetail(9L);

        // 仍列出用户提交过的项，只是 oldValue（当前实时值）无从对照为 null
        assertThat(vo.getDifferences()).isNotEmpty();
        assertThat(vo.getDifferences()).allMatch(d -> d.getOldValue() == null || d.getOldValue().isEmpty());
        assertThat(vo.getDishName()).isNull();
        assertThat(vo.getDishStatus()).isNull();
    }

    @Test
    @DisplayName("getDetail：处置结果来自处置任务表（同一抽屉只读态回显）")
    void getDetail_fillsTaskResult() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(true);
        ModerationTaskVO handled = new ModerationTaskVO();
        handled.setStatus(ModerationConst.STATUS_HANDLED);
        handled.setOutcome(ModerationConst.OUTCOME_REJECTED);
        handled.setReplyTitle("菜品信息未采纳");
        handled.setReplyContent("价格与档口今日公示一致");
        handled.setHandledByName("kingdo404");
        when(moderationTaskService.findByTypeAndId(eq(ModerationConst.TYPE_CORRECTION), anyLong()))
                .thenReturn(handled);

        CorrectionDetailVO vo = service().getDetail(9L);

        assertThat(vo.getStatus()).isEqualTo(ModerationConst.STATUS_HANDLED);
        assertThat(vo.getOutcome()).isEqualTo(ModerationConst.OUTCOME_REJECTED);
        assertThat(vo.getReplyContent()).isEqualTo("价格与档口今日公示一致");
        assertThat(vo.getHandledByName()).isEqualTo("kingdo404");
    }

    // ==================== reject：结论委派 ====================

    private static ModerationHandleReq handleReq(String outcome, String title, String content) {
        ModerationHandleReq req = new ModerationHandleReq();
        req.setOutcome(outcome);
        req.setReplyTitle(title);
        req.setReplyContent(content);
        return req;
    }

    @Test
    @DisplayName("reject：纠错不存在 → 4001（目标态契约）")
    void reject_notFound_4001() {
        when(correctionMapper.selectById(anyLong())).thenReturn(null);
        assertThatThrownBy(() -> service().reject(9L, handleReq(null, null, "价格一致"), 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
    }

    @Test
    @DisplayName("reject：已处置（非 pending）→ 400（幂等）")
    void reject_alreadyHandled_400() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        ModerationTaskVO handled = new ModerationTaskVO();
        handled.setStatus(ModerationConst.STATUS_HANDLED);
        when(moderationTaskService.findByTypeAndId(eq(ModerationConst.TYPE_CORRECTION), anyLong()))
                .thenReturn(handled);

        assertThatThrownBy(() -> service().reject(9L, handleReq(null, null, "价格一致"), 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(moderationTaskService, never()).handle(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("reject：结论非 rejected → 400（本端点仅支持不采纳）")
    void reject_illegalOutcome_400() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));

        assertThatThrownBy(() -> service().reject(9L, handleReq(ModerationConst.OUTCOME_HANDLED, null, "价格一致"), 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(moderationTaskService, never()).handle(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("reject：field 型委派（outcome=rejected、标题留空由缺省文案兜底、文案即不采纳原因）")
    void reject_field_delegatesRejected() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));

        service().reject(9L, handleReq(null, "  ", "价格与公示一致"), 3L);

        verify(moderationTaskService).handle(ModerationConst.TYPE_CORRECTION, 9L, ModerationConst.OUTCOME_REJECTED,
                null, "价格与公示一致", 3L, 1L);
    }

    @Test
    @DisplayName("reject：gone 型标题缺省为「菜品仍在售」（语义与 field 型的「未采纳」不同）")
    void reject_gone_usesStillOnSaleTitle() {
        Correction gone = pendingCorrection(1L);
        gone.setType(CorrectionConst.TYPE_GONE);
        when(correctionMapper.selectById(anyLong())).thenReturn(gone);

        service().reject(9L, handleReq(null, null, "今日临时售罄，明天恢复"), 3L);

        verify(moderationTaskService).handle(ModerationConst.TYPE_CORRECTION, 9L, ModerationConst.OUTCOME_REJECTED,
                CorrectionConst.GONE_REJECT_TITLE, "今日临时售罄，明天恢复", 3L, 1L);
    }

    @Test
    @DisplayName("reject：管理员自填标题优先（不覆盖人工措辞）")
    void reject_customTitleWins() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));

        service().reject(9L, handleReq(null, "信息有误已核实", "价格与公示一致"), 3L);

        verify(moderationTaskService).handle(ModerationConst.TYPE_CORRECTION, 9L, ModerationConst.OUTCOME_REJECTED,
                "信息有误已核实", "价格与公示一致", 3L, 1L);
    }

}