package com.bjtufood.correction.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.canteen.dto.StallBriefVO;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.correction.constant.CorrectionConst;
import com.bjtufood.correction.dto.CorrectionAdminVO;
import com.bjtufood.correction.dto.CorrectionDetailVO;
import com.bjtufood.correction.dto.DishCorrectionAdoptReq;
import com.bjtufood.correction.dto.DishCorrectionDifferenceVO;
import com.bjtufood.correction.dto.DishCorrectionReq;
import com.bjtufood.correction.dto.StallConfirmVO;
import com.bjtufood.correction.entity.Correction;
import com.bjtufood.correction.mapper.CorrectionMapper;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishCorrectionCmd;
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
 * 目的：把提交 / 处置的可见行为钉死。聚焦五类退化后即为安全 / 数据问题的逻辑：
 * <ol>
 *   <li><b>局部提交（patch）语义</b>：未传字段保持未改动（落库 NULL），空请求体 400「未提交任何改动」；</li>
 *   <li><b>机审合并送检</b>：多文本字段须合并为<b>单次</b> {@code checkText}（按调用计费）；
 *       纯 price / images 改动<b>不送检</b>；</li>
 *   <li><b>处置前置校验</b>：处置任务非 pending 一律 400（重复点击 = 已处理），菜品物理删除 4001；</li>
 *   <li><b>两段式档口确认</b>：未命中且未确认新建 ⇒ 只返回候选、<b>不得写回 dish / stall</b>；</li>
 *   <li><b>楼层纠错</b>：floor 传入即非空 / ∈ 楼层字典（值即汉字）/ ≤16 字校验；仅改楼层也算「有改动」；
 *       采纳时 floor 写回<b>目标档口</b>（{@code stall.floor}）而非 dish，且档口未落定前不得写。</li>
 * </ol>
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

    /** 采纳请求：只勾选给定差异项（逐项采纳） */
    private static DishCorrectionAdoptReq adoptReq(String... fields) {
        DishCorrectionAdoptReq req = new DishCorrectionAdoptReq();
        req.setAcceptedFields(List.of(fields));
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
        when(dishService.mapStallIdByIds(any())).thenReturn(Map.of(3L, 10L));
        when(dishService.countByStallId(anyLong())).thenReturn(4L);

        List<CorrectionAdminVO> records = service().listForAdmin(null, null, null, 1, 10).getRecords();

        assertThat(records).hasSize(2);
        assertThat(records.get(0).getFloor()).isEqualTo("二层");
        assertThat(records.get(0).getFloorImpact()).isEqualTo("将同步修改该档口下 4 个菜品");
        assertThat(records.get(1).getFloor()).isNull();
    }

    // ==================== adopt：前置校验与两段式档口确认 ====================

    @Test
    @DisplayName("adopt：纠错不存在 → 4001（目标态契约，见 docs/api/web/corrections.md）")
    void adopt_notFound_4001() {
        when(correctionMapper.selectById(anyLong())).thenReturn(null);
        assertThatThrownBy(() -> service().adopt(9L, null, 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
    }

    @Test
    @DisplayName("adopt：处置任务缺失（数据不一致）→ 4001（不当成「已处理」误导管理员）")
    void adopt_taskMissing_4001() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(moderationTaskService.findByTypeAndId(eq(ModerationConst.TYPE_CORRECTION), anyLong())).thenReturn(null);

        assertThatThrownBy(() -> service().adopt(9L, adoptReq("name"), 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(dishService, never()).applyCorrection(any());
    }

    @Test
    @DisplayName("adopt：已处置（非 pending）→ 400（幂等：重复点击读作「该反馈已处理」）")
    void adopt_alreadyHandled_400() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        ModerationTaskVO handled = new ModerationTaskVO();
        handled.setStatus(ModerationConst.STATUS_HANDLED);
        when(moderationTaskService.findByTypeAndId(eq(ModerationConst.TYPE_CORRECTION), anyLong()))
                .thenReturn(handled);

        assertThatThrownBy(() -> service().adopt(9L, adoptReq("name"), 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(dishService, never()).applyCorrection(any());
    }

    @Test
    @DisplayName("adopt：目标菜品已被物理删除 → 4001 且不写回")
    void adopt_dishDeleted_4001() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(false);

        assertThatThrownBy(() -> service().adopt(9L, adoptReq("name"), 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(dishService, never()).applyCorrection(any());
    }

    @Test
    @DisplayName("adopt：档口名未命中且未确认新建 → 只返回候选，**不得写回 dish、不得归档**")
    void adopt_unmatchedStall_returnsCandidatesWithoutWriting() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.findIdByName(any())).thenReturn(null);
        StallBriefVO s = mock(StallBriefVO.class);
        when(s.getId()).thenReturn(11L);
        when(s.getName()).thenReturn("清真面档B");
        when(stallService.listBriefCandidates(any())).thenReturn(List.of(s));

        StallConfirmVO result = service().adopt(9L, adoptReq("stallName"), 1L);

        assertThat(result).isNotNull();
        // record 访问器：needStallConfirm() / candidates()
        assertThat(result.needStallConfirm()).isTrue();
        assertThat(result.candidates()).hasSize(1);
        // 关键：未确认前绝不写回，也不把反馈标记为已处置
        verify(dishService, never()).applyCorrection(any());
        verify(moderationTaskService, never()).handle(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("adopt：显式 stallId 不存在 → 400")
    void adopt_explicitStallMissing_400() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.existsById(anyLong())).thenReturn(false);
        DishCorrectionAdoptReq req = adoptReq("stallName");
        req.setStallId(404L);

        assertThatThrownBy(() -> service().adopt(9L, req, 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(dishService, never()).applyCorrection(any());
    }

    @Test
    @DisplayName("adopt：档口名精确命中 → 写回 dish + 归档 handled（缺省回执文案交由处置任务服务）")
    void adopt_matchedStall_appliesAndArchives() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.findIdByName(any())).thenReturn(10L);
        when(dishService.applyCorrection(any())).thenReturn(true);

        // 命中即直接采纳，无需二次确认（采纳了档口项才走两段式解析）
        assertThat(service().adopt(9L, adoptReq("stallName"), 3L)).isNull();

        ArgumentCaptor<DishCorrectionCmd> cmd = ArgumentCaptor.forClass(DishCorrectionCmd.class);
        verify(dishService).applyCorrection(cmd.capture());
        assertThat(cmd.getValue().getDishId()).isEqualTo(3L);
        assertThat(cmd.getValue().getStallId()).isEqualTo(10L);

        // 提交行不被改写（append-only）：处置结果只落在处置任务行
        verify(correctionMapper, never()).updateById(any());

        // 只采纳档口项 ⇒ 部分采纳：缺省回执文案逐项告知（未填附注时不至于让用户以为全被采纳）
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(moderationTaskService).handle(eq(ModerationConst.TYPE_CORRECTION), eq(9L),
                eq(ModerationConst.OUTCOME_HANDLED), eq(null), content.capture(), eq(3L), eq(1L));
        assertThat(content.getValue()).contains("已采纳：档口名称").contains("未采纳：");
    }

    @Test
    @DisplayName("adopt：本次纠错含楼层改动 → 写回**目标档口** stall.floor（楼层归属档口，不写 dish）")
    void adopt_floorChanged_writesBackToResolvedStall() {
        Correction c = pendingCorrection(1L);
        c.setFloor("二层");
        when(correctionMapper.selectById(anyLong())).thenReturn(c);
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.findIdByName(any())).thenReturn(10L);
        when(dishService.applyCorrection(any())).thenReturn(true);

        assertThat(service().adopt(9L, adoptReq("stallName", "floor"), 1L)).isNull();

        // 落点 = 本次采纳解析出的目标档口（两段式确认的最终结果），而非提交名对应的档口
        verify(stallService).updateFloor(10L, "二层");
        // 楼层不进 dish 写回指令（DishCorrectionCmd 无 floor 字段）：菜品无楼层列
        ArgumentCaptor<DishCorrectionCmd> cmd = ArgumentCaptor.forClass(DishCorrectionCmd.class);
        verify(dishService).applyCorrection(cmd.capture());
        assertThat(cmd.getValue().getStallId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("adopt：只采纳 floor（未采纳档口项）→ 不触发两段式确认，楼层写回该菜**当前所属档口**")
    void adopt_floorOnlyWithoutStallItem_writesToCurrentStall() {
        Correction c = pendingCorrection(1L);
        c.setFloor("二层");
        c.setStallName(null);   // 档口名未改动的局部提交
        c.setCanteenName(null);
        when(correctionMapper.selectById(anyLong())).thenReturn(c);
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(dishService.applyCorrection(any())).thenReturn(true);

        // 只勾选 floor：档口未变 ⇒ 不需要（也不应要求）管理端确认档口
        assertThat(service().adopt(9L, adoptReq("floor"), 1L)).isNull();

        // 楼层写回该菜**当前所属档口**（liveDish.stallId=10L），而不是提交/指定的档口
        verify(stallService).updateFloor(10L, "二层");
        verify(stallService, never()).findIdByName(any());
    }

    @Test
    @DisplayName("adopt：本次纠错未改楼层（floor=null）→ 不触碰档口楼层（空值不得覆盖既有 stall.floor）")
    void adopt_floorAbsent_doesNotTouchStallFloor() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.findIdByName(any())).thenReturn(10L);
        when(dishService.applyCorrection(any())).thenReturn(true);

        assertThat(service().adopt(9L, adoptReq("stallName"), 1L)).isNull();

        verify(stallService, never()).updateFloor(any(), any());
        verify(dishService).applyCorrection(any());
    }

    @Test
    @DisplayName("adopt：档口名未命中且未确认新建（含楼层改动）→ 只返回候选，**楼层亦不得写回**")
    void adopt_unmatchedStallWithFloor_doesNotWriteFloor() {
        Correction c = pendingCorrection(1L);
        c.setFloor("二层");
        when(correctionMapper.selectById(anyLong())).thenReturn(c);
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.findIdByName(any())).thenReturn(null);
        StallBriefVO s = mock(StallBriefVO.class);
        when(s.getId()).thenReturn(11L);
        when(s.getName()).thenReturn("清真面档B");
        when(stallService.listBriefCandidates(any())).thenReturn(List.of(s));

        StallConfirmVO result = service().adopt(9L, adoptReq("stallName", "floor"), 1L);

        assertThat(result.needStallConfirm()).isTrue();
        // 关键：档口未落定前不写 dish、不写 stall.floor、不归档
        verify(stallService, never()).updateFloor(any(), any());
        verify(dishService, never()).applyCorrection(any());
        verify(moderationTaskService, never()).handle(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("adopt：写回返回 false（并发删除兜底）→ 4001 且不归档")
    void adopt_applyReturnsFalse_4001() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.findIdByName(any())).thenReturn(10L);
        when(dishService.applyCorrection(any())).thenReturn(false);

        assertThatThrownBy(() -> service().adopt(9L, adoptReq("stallName"), 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(moderationTaskService, never()).handle(any(), any(), any(), any(), any(), any(), any());
    }

    // ==================== 详情 + 逐项采纳 ====================

    @Test
    @DisplayName("adopt：acceptedFields 缺失或空 → 400 且不写回（「什么都不采纳」不是采纳，是驳回）")
    void adopt_acceptedFieldsMissingOrEmpty_400() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(true);

        assertThatThrownBy(() -> service().adopt(9L, null, 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> service().adopt(9L, adoptReq(), 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(dishService, never()).applyCorrection(any());
        verify(moderationTaskService, never()).handle(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("adopt：采纳项「已无差异」→ 400 且不写回（避免采纳一个已经相同的值）")
    void adopt_acceptedFieldAlreadySynced_400() {
        Correction c = pendingCorrection(1L);
        when(correctionMapper.selectById(anyLong())).thenReturn(c);
        when(dishService.existsById(anyLong())).thenReturn(true);
        CorrectionServiceImpl svc = service();
        // 实时菜品与快照**逐字段一致** ⇒ 差异清单为空 ⇒ 任何采纳项都无效
        DishAdminVO same = liveDish();
        same.setName(c.getName());
        same.setCanteenName(c.getCanteenName());
        same.setStallName(c.getStallName());
        when(dishService.getForAdmin(anyLong())).thenReturn(same);

        assertThatThrownBy(() -> svc.adopt(9L, adoptReq("name"), 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(dishService, never()).applyCorrection(any());
    }

    @Test
    @DisplayName("adopt：部分采纳 → 缺省回执文案逐项告知已采纳 / 未采纳项（用户不会重复提交同一项）")
    void adopt_partial_usesFieldLevelNotice() {
        Correction c = pendingCorrection(1L);
        c.setPrice(1600);   // 快照含价格改动
        when(correctionMapper.selectById(anyLong())).thenReturn(c);
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(dishService.applyCorrection(any())).thenReturn(true);

        service().adopt(9L, adoptReq("name"), 1L);

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(moderationTaskService).handle(eq(ModerationConst.TYPE_CORRECTION), eq(9L),
                eq(ModerationConst.OUTCOME_HANDLED), eq(null), content.capture(), any(), any());
        // 差异项：name / canteenName / stallName / price（liveDish 处处不同）⇒ 未采纳项必须点名
        assertThat(content.getValue()).contains("已采纳：菜品名称").contains("未采纳：");
    }

    @Test
    @DisplayName("getDetail：differences 只列「仍有差异」项，oldValue 取实时值（不是快照值）")
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
    }

    @Test
    @DisplayName("getDetail：目标菜品已物理删除 → differences 为空且不抛（采纳本身另有 4001）")
    void getDetail_dishDeleted_emptyDifferences() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(false);
        CorrectionServiceImpl svc = service();

        CorrectionDetailVO vo = svc.getDetail(9L);

        assertThat(vo.getDifferences()).isEmpty();
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

    // ==================== 采纳：gone 型仅下架 ====================

    @Test
    @DisplayName("adopt：gone 型 → 仅下架菜品（不删除），归档 handled 且缺省文案「已下架，感谢反馈」")
    void adopt_gone_takesOffShelfOnly() {
        Correction gone = pendingCorrection(1L);
        gone.setType(CorrectionConst.TYPE_GONE);
        when(correctionMapper.selectById(anyLong())).thenReturn(gone);
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(dishService.existsOnSale(anyLong())).thenReturn(true);

        assertThat(service().adopt(9L, null, 3L)).isNull();

        // 只调 updateStatus（可逆），绝不触碰删除路径
        verify(dishService).updateStatus(3L, "off");
        verify(dishService, never()).applyCorrection(any());
        verify(moderationTaskService).handle(ModerationConst.TYPE_CORRECTION, 9L, ModerationConst.OUTCOME_HANDLED,
                CorrectionConst.GONE_ADOPT_TITLE, CorrectionConst.GONE_ADOPT_REPLY, 3L, 1L);
    }

    @Test
    @DisplayName("adopt：gone 型菜品已下架 → 幂等（不重复写状态），仍归档 + 回执")
    void adopt_gone_alreadyOffShelf_isIdempotent() {
        Correction gone = pendingCorrection(1L);
        gone.setType(CorrectionConst.TYPE_GONE);
        when(correctionMapper.selectById(anyLong())).thenReturn(gone);
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(dishService.existsOnSale(anyLong())).thenReturn(false);

        assertThat(service().adopt(9L, null, 3L)).isNull();

        verify(dishService, never()).updateStatus(anyLong(), anyString());
        verify(moderationTaskService).handle(any(), any(), any(), any(), any(), any(), any());
    }
}
