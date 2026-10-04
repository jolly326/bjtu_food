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
import com.bjtufood.correction.dto.DishCorrectionAdoptReq;
import com.bjtufood.correction.dto.DishCorrectionAdminVO;
import com.bjtufood.correction.dto.DishCorrectionHandleReq;
import com.bjtufood.correction.dto.DishCorrectionReq;
import com.bjtufood.correction.dto.StallConfirmVO;
import com.bjtufood.correction.entity.DishCorrection;
import com.bjtufood.correction.mapper.DishCorrectionMapper;
import com.bjtufood.dish.dto.DishCorrectionCmd;
import com.bjtufood.correction.dto.DishCorrectionDetailVO;
import com.bjtufood.correction.dto.DishCorrectionDifferenceVO;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.service.DishAttributeAdminService;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
import com.bjtufood.notification.dto.NotificationCmd;
import com.bjtufood.notification.service.NotificationService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CorrectionServiceImpl} 单元测试。
 * <p>
 * 目的与 {@link com.bjtufood.feedback.service.impl.FeedbackServiceImplTest} 一致：在「机审移出事务边界」
 * 的重构之前把当前行为钉死，重构后直接复用本类断言「可见行为未变」。
 * <p>
 * 聚焦五类退化后即为安全 / 数据问题的逻辑：
 * <ol>
 *   <li><b>局部提交（patch）语义</b>：未传字段保持未改动（落库 NULL），空请求体 400「未提交任何改动」；</li>
 *   <li><b>机审合并送检</b>：多文本字段须合并为<b>单次</b> {@code checkText}（按调用计费，
 *       若退化成分字段多次送检会把微信额度放大 4 倍）；纯 price/images 改动<b>不送检</b>；</li>
 *   <li><b>采纳幂等与前置校验</b>：非 pending 一律 400（重复点击 = 已处理），菜品物理删除 4001；</li>
 *   <li><b>两段式档口确认</b>：未命中且未确认新建 ⇒ 只返回候选、<b>不得写回 dish</b>；</li>
 *   <li><b>回执投递判据</b>：<b>登录级</b>——userId 非空即投递（2026-10-01 拍板放宽：
 *       消息中心是登录级能力，游客也应收到自己反馈的处理结果）；
 *       游客（userId=null）不投递（无归属可投）；</li>
 *   <li><b>楼层纠错</b>：floor 传入即非空 / ≤16 字校验；仅改楼层也算「有改动」；
 *       采纳时 floor 写回<b>目标档口</b>（{@code stall.floor}）而非 dish，且档口未落定前不得写。</li>
 * </ol>
 * 被测类为纯 POJO：{@code @Transactional} 依赖 Spring 代理，单测中不生效，断言的是方法体内业务逻辑。
 */
class CorrectionServiceImplTest {

    /** 纯 Mockito 无 Spring 上下文，MyBatis-Plus 的 lambda 缓存需显式初始化 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), CorrectionServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, DishCorrection.class);
    }

    private final DishCorrectionMapper correctionMapper = mock(DishCorrectionMapper.class);
    private final DishService dishService = mock(DishService.class);
    private final StallService stallService = mock(StallService.class);
    private final UserService userService = mock(UserService.class);
    private final LocalSensitiveFilter localSensitiveFilter = mock(LocalSensitiveFilter.class);
    private final ContentSecurityService contentSecurityService = mock(ContentSecurityService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ImageUrlUtil imageUrlUtil = mock(ImageUrlUtil.class);

    /** 构造器参数顺序须与 {@code CorrectionServiceImpl} 的 final 字段声明顺序逐字一致（@RequiredArgsConstructor） */
    private CorrectionServiceImpl service() {
        // B4 逐项采纳的前置：采纳项必须「此刻仍有差异」⇒ 详情对照需一份可取的**实时菜品**。
        // 默认给「处处不同于快照」的实时值（各用例只关心档口 / 楼层分支，不关心具体差异内容）。
        when(dishService.getForAdmin(anyLong())).thenReturn(liveDish());
        // 落库 Bean 用**真实实现**包裹 mock 的 mapper：事务边界收窄（机审移出事务）后，
        // 本类断言仍原样落在 correctionMapper.insert 上 —— 即「可见行为未变」的直接证据。
        return new CorrectionServiceImpl(correctionMapper, new CorrectionPersister(correctionMapper), dishService,
                mock(DishAttributeAdminService.class), stallService, userService, localSensitiveFilter,
                contentSecurityService, notificationService, imageUrlUtil);
    }

    /** 实时菜品（处处不同于 {@link #pendingCorrection} 的快照，使 name/canteenName/stallName 都是「仍有差异」项） */
    private static DishAdminVO liveDish() {
        DishAdminVO live = new DishAdminVO();
        live.setId(3L);
        live.setStallId(10L);
        live.setName("旧菜名");
        live.setCanteenName("旧食堂");
        live.setStallName("旧档口");
        live.setPrice(1200);
        live.setImages(List.of());
        live.setAttributes(Map.of());
        return live;
    }

    /** 采纳请求：只勾选给定差异项（B4「逐项采纳」） */
    private static DishCorrectionAdoptReq adoptReq(String... fields) {
        DishCorrectionAdoptReq req = new DishCorrectionAdoptReq();
        req.setAcceptedFields(List.of(fields));
        return req;
    }

    private DishCorrectionReq req() {
        return new DishCorrectionReq();
    }

    private DishCorrection pendingCorrection(Long userId) {
        DishCorrection c = new DishCorrection();
        c.setId(9L);
        c.setDishId(3L);
        c.setUserId(userId);
        c.setName("牛肉拉面");
        c.setCanteenName("清真食堂");
        c.setStallName("清真面档");
        c.setStatus(CorrectionConst.STATUS_PENDING);
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
    @DisplayName("submit：仅改 price / 配图（无文本）→ 不发起机审（无文本可检，省额度）")
    void submit_priceOnly_noSecCheck() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        when(imageUrlUtil.isValidCosUgcUrl(any())).thenReturn(true);
        DishCorrectionReq req = req();
        req.setPrice(1200);

        service().submit(7L, 3L, req);

        verify(contentSecurityService, never()).checkText(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        ArgumentCaptor<DishCorrection> captor = ArgumentCaptor.forClass(DishCorrection.class);
        verify(correctionMapper).insert(captor.capture());
        // patch 语义：未传字段保持 NULL（不覆盖菜品既有值）
        assertThat(captor.getValue().getName()).isNull();
        assertThat(captor.getValue().getCanteenName()).isNull();
        assertThat(captor.getValue().getStallName()).isNull();
        assertThat(captor.getValue().getPrice()).isEqualTo(1200);
        assertThat(captor.getValue().getStatus()).isEqualTo(CorrectionConst.STATUS_PENDING);
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
    @DisplayName("submit：楼层恰好 16 字 → 放行（边界值，不得误判超长）")
    void submit_floorAtMaxLength_persists() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        String max = "层".repeat(CorrectionConst.FLOOR_MAX_LENGTH);
        DishCorrectionReq req = req();
        req.setFloor(max);

        service().submit(1L, 3L, req);

        ArgumentCaptor<DishCorrection> captor = ArgumentCaptor.forClass(DishCorrection.class);
        verify(correctionMapper).insert(captor.capture());
        assertThat(captor.getValue().getFloor()).isEqualTo(max);
    }

    @Test
    @DisplayName("submit：**仅改楼层**（其余改动项未传）算「有改动」→ 落库 floor 快照，不得判 400「未提交任何改动」")
    void submit_floorOnly_persistsAsChange() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        DishCorrectionReq req = req();
        req.setFloor("  2F  ");   // 传入值 trim 后落库

        service().submit(1L, 3L, req);

        ArgumentCaptor<DishCorrection> captor = ArgumentCaptor.forClass(DishCorrection.class);
        verify(correctionMapper).insert(captor.capture());
        DishCorrection saved = captor.getValue();
        assertThat(saved.getFloor()).isEqualTo("2F");
        assertThat(saved.getDishId()).isEqualTo(3L);
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo(CorrectionConst.STATUS_PENDING);
        // 其余改动项保持 NULL（patch 语义：未改动不落库、采纳时不覆盖既有值）
        assertThat(saved.getName()).isNull();
        assertThat(saved.getPrice()).isNull();
        assertThat(saved.getCanteenName()).isNull();
        assertThat(saved.getStallName()).isNull();
        assertThat(saved.getAttributes()).isNull();
    }

    @Test
    @DisplayName("submit：楼层为自由文本 → 与其他自由文本字段合并为**单次**机审（采纳后进入公开展示）")
    void submit_floorIncludedInSingleSecCheckCall() {
        when(dishService.existsOnSale(anyLong())).thenReturn(true);
        UserAuthContextVO user = new UserAuthContextVO();
        user.setOpenid("oXxx");
        when(userService.getAuthContext(7L)).thenReturn(user);

        DishCorrectionReq req = req();
        req.setStallName("清真面档");
        req.setFloor("2F");
        service().submit(7L, 3L, req);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(contentSecurityService).checkText(org.mockito.ArgumentMatchers.eq("oXxx"), text.capture(),
                org.mockito.ArgumentMatchers.eq(2));
        // 楼层与其它自由文本字段同批送检：漏检即可把违规文本写进公开可见的 stall.floor
        assertThat(text.getValue()).isEqualTo("清真面档\n2F");
    }

    @Test
    @DisplayName("管理端列表：floor 装配进 VO（提交的楼层快照对后台可见，未改动为 null）")
    void listForAdmin_assemblesFloor() {
        DishCorrection withFloor = pendingCorrection(1L);
        withFloor.setFloor("2F");
        DishCorrection withoutFloor = pendingCorrection(2L);
        Page<DishCorrection> page = new Page<>(1, 10);
        page.setRecords(List.of(withFloor, withoutFloor));
        page.setTotal(2);
        when(correctionMapper.selectPage(any(), any())).thenReturn(page);
        when(userService.mapNicknameByIds(any())).thenReturn(Map.of(1L, "交大干饭王", 2L, "食堂常客"));
        when(dishService.mapNameByIds(any())).thenReturn(Map.of(3L, "牛肉拉面"));

        List<DishCorrectionAdminVO> records = service().listForAdmin(null, null, 1, 10).getRecords();

        assertThat(records).hasSize(2);
        assertThat(records.get(0).getFloor()).isEqualTo("2F");
        assertThat(records.get(1).getFloor()).isNull();
    }

    // ==================== adopt：幂等与两段式档口确认 ====================

    @Test
    @DisplayName("adopt：纠错不存在 → 4001（目标态契约，见 docs/api/web/corrections.md）")
    void adopt_notFound_4001() {
        when(correctionMapper.selectById(anyLong())).thenReturn(null);
        assertThatThrownBy(() -> service().adopt(9L, null))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
    }

    @Test
    @DisplayName("adopt：已处理（非 pending）→ 400（幂等：重复点击读作「该纠错已处理」）")
    void adopt_alreadyHandled_400() {
        DishCorrection c = pendingCorrection(1L);
        c.setStatus(CorrectionConst.STATUS_ADOPTED);
        when(correctionMapper.selectById(anyLong())).thenReturn(c);

        assertThatThrownBy(() -> service().adopt(9L, null))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(dishService, never()).applyCorrection(any());
    }

    @Test
    @DisplayName("adopt：目标菜品已被物理删除 → 4001 且不写回")
    void adopt_dishDeleted_4001() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(false);

        assertThatThrownBy(() -> service().adopt(9L, null))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(dishService, never()).applyCorrection(any());
    }

    @Test
    @DisplayName("adopt：档口名未命中且未确认新建 → 只返回候选，**不得写回 dish、不得改状态**")
    void adopt_unmatchedStall_returnsCandidatesWithoutWriting() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.findIdByName(any())).thenReturn(null);
        StallBriefVO s = mock(StallBriefVO.class);
        when(s.getId()).thenReturn(11L);
        when(s.getName()).thenReturn("清真面档B");
        when(stallService.listBriefCandidates(any())).thenReturn(List.of(s));

        StallConfirmVO result = service().adopt(9L, adoptReq("stallName"));

        assertThat(result).isNotNull();
        // record 访问器：needStallConfirm() / candidates()
        assertThat(result.needStallConfirm()).isTrue();
        assertThat(result.candidates()).hasSize(1);
        // 关键：未确认前绝不写回，也不把纠错标记为已处理
        verify(dishService, never()).applyCorrection(any());
        verify(correctionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("adopt：显式 stallId 不存在 → 400")
    void adopt_explicitStallMissing_400() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.existsById(anyLong())).thenReturn(false);
        DishCorrectionAdoptReq req = adoptReq("stallName");
        req.setStallId(404L);

        assertThatThrownBy(() -> service().adopt(9L, req))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(dishService, never()).applyCorrection(any());
    }

    @Test
    @DisplayName("adopt：档口名精确命中 → 写回 dish + 归档 adopted + 投递回执")
    void adopt_matchedStall_appliesAndArchives() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.findIdByName(any())).thenReturn(10L);
        when(stallService.getNameById(10L)).thenReturn("清真面档");
        when(dishService.applyCorrection(any())).thenReturn(true);

        // 命中即直接采纳，无需二次确认（采纳了档口项才走两段式解析）
        assertThat(service().adopt(9L, adoptReq("stallName"))).isNull();

        ArgumentCaptor<DishCorrectionCmd> cmd = ArgumentCaptor.forClass(DishCorrectionCmd.class);
        verify(dishService).applyCorrection(cmd.capture());
        assertThat(cmd.getValue().getDishId()).isEqualTo(3L);
        assertThat(cmd.getValue().getStallId()).isEqualTo(10L);

        ArgumentCaptor<DishCorrection> saved = ArgumentCaptor.forClass(DishCorrection.class);
        verify(correctionMapper).updateById(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CorrectionConst.STATUS_ADOPTED);
        assertThat(saved.getValue().getReply()).isEqualTo(CorrectionConst.ADOPT_REPLY);
        assertThat(saved.getValue().getRejectReason()).isNull();
        assertThat(saved.getValue().getHandledAt()).isNotNull();

        verify(notificationService).notify(any(NotificationCmd.class));
    }

    @Test
    @DisplayName("adopt：本次纠错含楼层改动 → 写回**目标档口** stall.floor（楼层归属档口，不写 dish）")
    void adopt_floorChanged_writesBackToResolvedStall() {
        DishCorrection c = pendingCorrection(1L);
        c.setFloor("2F");
        when(correctionMapper.selectById(anyLong())).thenReturn(c);
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.findIdByName(any())).thenReturn(10L);
        when(stallService.getNameById(10L)).thenReturn("清真面档");
        when(dishService.applyCorrection(any())).thenReturn(true);

        assertThat(service().adopt(9L, adoptReq("stallName", "floor"))).isNull();

        // 落点 = 本次采纳解析出的目标档口（两段式确认的最终结果），而非提交名对应的档口
        verify(stallService).updateFloor(10L, "2F");
        // 楼层不进 dish 写回指令（DishCorrectionCmd 无 floor 字段）：菜品无楼层列
        ArgumentCaptor<DishCorrectionCmd> cmd = ArgumentCaptor.forClass(DishCorrectionCmd.class);
        verify(dishService).applyCorrection(cmd.capture());
        assertThat(cmd.getValue().getStallId()).isEqualTo(10L);
        verify(correctionMapper).updateById(any());
    }

    @Test
    @DisplayName("adopt：只采纳 floor（未采纳档口项）→ 不触发两段式确认，楼层写回该菜**当前所属档口**，且归档不 NPE")
    void adopt_floorOnlyWithoutStallItem_writesToCurrentStall() {
        DishCorrection c = pendingCorrection(1L);
        c.setFloor("B1");
        c.setStallName(null);   // 档口名未改动的局部提交
        c.setCanteenName(null);
        when(correctionMapper.selectById(anyLong())).thenReturn(c);
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(dishService.applyCorrection(any())).thenReturn(true);

        // 只勾选 floor：档口未变 ⇒ 不需要（也不应要求）管理端确认档口
        assertThat(service().adopt(9L, adoptReq("floor"))).isNull();

        // 楼层写回该菜**当前所属档口**（liveDish.stallId=10L），而不是提交/指定的档口
        verify(stallService).updateFloor(10L, "B1");
        // 档口名快照为 null 时归档也必须成功（原实现在此 NPE ⇒ 采纳 500）；未采纳档口项 ⇒ 不重命名
        ArgumentCaptor<DishCorrection> saved = ArgumentCaptor.forClass(DishCorrection.class);
        verify(correctionMapper).updateById(saved.capture());
        assertThat(saved.getValue().getStallName()).isNull();
        assertThat(saved.getValue().getStatus()).isEqualTo(CorrectionConst.STATUS_ADOPTED);
    }

    @Test
    @DisplayName("adopt：本次纠错未改楼层（floor=null）→ 不触碰档口楼层（空值不得覆盖既有 stall.floor）")
    void adopt_floorAbsent_doesNotTouchStallFloor() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.findIdByName(any())).thenReturn(10L);
        when(stallService.getNameById(10L)).thenReturn("清真面档");
        when(dishService.applyCorrection(any())).thenReturn(true);

        assertThat(service().adopt(9L, adoptReq("stallName"))).isNull();

        verify(stallService, never()).updateFloor(any(), any());
        verify(dishService).applyCorrection(any());
    }

    @Test
    @DisplayName("adopt：档口名未命中且未确认新建（含楼层改动）→ 只返回候选，**楼层亦不得写回**")
    void adopt_unmatchedStallWithFloor_doesNotWriteFloor() {
        DishCorrection c = pendingCorrection(1L);
        c.setFloor("2F");
        when(correctionMapper.selectById(anyLong())).thenReturn(c);
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.findIdByName(any())).thenReturn(null);
        StallBriefVO s = mock(StallBriefVO.class);
        when(s.getId()).thenReturn(11L);
        when(s.getName()).thenReturn("清真面档B");
        when(stallService.listBriefCandidates(any())).thenReturn(List.of(s));

        StallConfirmVO result = service().adopt(9L, adoptReq("stallName", "floor"));

        assertThat(result.needStallConfirm()).isTrue();
        // 关键：档口未落定前不写 dish、不写 stall.floor、不改纠错状态
        verify(stallService, never()).updateFloor(any(), any());
        verify(dishService, never()).applyCorrection(any());
        verify(correctionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("adopt：写回返回 false（并发删除兜底）→ 4001 且不归档")
    void adopt_applyReturnsFalse_4001() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.findIdByName(any())).thenReturn(10L);
        when(stallService.getNameById(10L)).thenReturn("清真面档");
        when(dishService.applyCorrection(any())).thenReturn(false);

        assertThatThrownBy(() -> service().adopt(9L, adoptReq("stallName")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(correctionMapper, never()).updateById(any());
    }

    // ==================== B4 详情 + 逐项采纳（2026-10-03 新增能力的护栏） ====================

    @Test
    @DisplayName("adopt：acceptedFields 缺失或空 → 400 且不写回（「什么都不采纳」不是采纳，是拒绝）")
    void adopt_acceptedFieldsMissingOrEmpty_400() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(true);

        assertThatThrownBy(() -> service().adopt(9L, null))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> service().adopt(9L, adoptReq()))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(dishService, never()).applyCorrection(any());
        verify(correctionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("adopt：采纳项「已无差异」→ 400 且不写回（避免采纳一个已经相同的值）")
    void adopt_acceptedFieldAlreadySynced_400() {
        DishCorrection c = pendingCorrection(1L);
        when(correctionMapper.selectById(anyLong())).thenReturn(c);
        when(dishService.existsById(anyLong())).thenReturn(true);
        CorrectionServiceImpl svc = service();
        // 实时菜品与快照**逐字段一致** ⇒ 差异清单为空 ⇒ 任何采纳项都无效
        DishAdminVO same = liveDish();
        same.setName(c.getName());
        same.setCanteenName(c.getCanteenName());
        same.setStallName(c.getStallName());
        when(dishService.getForAdmin(anyLong())).thenReturn(same);

        assertThatThrownBy(() -> svc.adopt(9L, adoptReq("name")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(dishService, never()).applyCorrection(any());
    }

    @Test
    @DisplayName("getDetail：differences 只列「仍有差异」项，oldValue 取实时值（不是快照值）")
    void getDetail_listsOnlyRemainingDifferencesWithLiveValues() {
        DishCorrection c = pendingCorrection(1L);
        c.setPrice(1600);   // 快照含价格改动
        when(correctionMapper.selectById(anyLong())).thenReturn(c);
        when(dishService.existsById(anyLong())).thenReturn(true);
        CorrectionServiceImpl svc = service();
        when(dishService.mapNameByIds(any())).thenReturn(Map.of(3L, "牛肉拉面"));
        when(userService.mapNicknameByIds(any())).thenReturn(Map.of(1L, "交大干饭王"));

        DishCorrectionDetailVO vo = svc.getDetail(9L);

        assertThat(vo.getDishName()).isEqualTo("牛肉拉面");
        assertThat(vo.getDifferences()).extracting(DishCorrectionDifferenceVO::getField)
                .containsExactlyInAnyOrder("name", "canteenName", "stallName", "price");
        // oldValue = 实时值（liveDish 的「旧菜名」），而非快照的「牛肉拉面」
        DishCorrectionDifferenceVO nameDiff = vo.getDifferences().stream()
                .filter(d -> "name".equals(d.getField())).findFirst().orElseThrow();
        assertThat(nameDiff.getOldValue()).isEqualTo("旧菜名");
        assertThat(nameDiff.getNewValue()).isEqualTo("牛肉拉面");
        // 提交快照（仅改动项）
        assertThat(vo.getSubmitted()).containsEntry("name", "牛肉拉面").containsEntry("price", 1600);
    }

    @Test
    @DisplayName("getDetail：目标菜品已物理删除 → differences 为空且不抛（采纳本身另有 4001）")
    void getDetail_dishDeleted_emptyDifferences() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(false);
        CorrectionServiceImpl svc = service();

        DishCorrectionDetailVO vo = svc.getDetail(9L);

        assertThat(vo.getDifferences()).isEmpty();
        assertThat(vo.getDishName()).isNull();
    }

    // ==================== reject：结论与必填校验 ====================

    private DishCorrectionHandleReq handleReq(String outcome, String reply, String reason) {
        DishCorrectionHandleReq req = new DishCorrectionHandleReq();
        req.setOutcome(outcome);
        req.setReply(reply);
        req.setRejectReason(reason);
        return req;
    }

    @Test
    @DisplayName("reject：纠错不存在 → 4001（目标态契约）")
    void reject_notFound_4001() {
        when(correctionMapper.selectById(anyLong())).thenReturn(null);
        assertThatThrownBy(() -> service().reject(9L, handleReq(null, "已核实", "价格一致")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
    }

    @Test
    @DisplayName("reject：已处理（非 pending）→ 400（幂等）")
    void reject_alreadyHandled_400() {
        DishCorrection c = pendingCorrection(1L);
        c.setStatus(CorrectionConst.STATUS_REJECTED);
        when(correctionMapper.selectById(anyLong())).thenReturn(c);

        assertThatThrownBy(() -> service().reject(9L, handleReq(null, "已核实", "价格一致")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(correctionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("reject：结论非 rejected → 400（本端点仅支持不采纳）")
    void reject_illegalOutcome_400() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        assertThatThrownBy(() -> service().reject(9L, handleReq("handled", "已核实", "价格一致")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(correctionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("reject：不采纳原因 为空或纯空白 → 400（**回复留空合法**：B4 起 reply 可选）")
    void reject_blankReason_400_replyOptional() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));

        // 不采纳原因缺失 / 纯空白 → 400（必填不变）
        assertThatThrownBy(() -> service().reject(9L, handleReq(null, "已核实", "   ")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> service().reject(9L, handleReq(null, "已核实", null)))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(correctionMapper, never()).updateById(any());

        // 回复留空 + 原因非空 ⇒ **合法**（回执正文退化为不采纳原因）
        service().reject(9L, handleReq(null, "  ", "价格一致"));
        ArgumentCaptor<DishCorrection> saved = ArgumentCaptor.forClass(DishCorrection.class);
        verify(correctionMapper).updateById(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CorrectionConst.STATUS_REJECTED);
        assertThat(saved.getValue().getRejectReason()).isEqualTo("价格一致");
    }

    @Test
    @DisplayName("reject：不采纳原因超长 → 400")
    void reject_reasonTooLong_400() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        assertThatThrownBy(() -> service().reject(9L,
                handleReq(null, "已核实", "不".repeat(CorrectionConst.REJECT_REASON_MAX_LENGTH + 1))))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(correctionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("reject：正常拒绝 → 归档 rejected + 回执携带不采纳原因")
    void reject_valid_archivesAndNotifies() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));

        service().reject(9L, handleReq(null, "  已核实  ", "价格与公示一致"));

        ArgumentCaptor<DishCorrection> saved = ArgumentCaptor.forClass(DishCorrection.class);
        verify(correctionMapper).updateById(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CorrectionConst.STATUS_REJECTED);
        assertThat(saved.getValue().getReply()).isEqualTo("已核实");
        assertThat(saved.getValue().getRejectReason()).isEqualTo("价格与公示一致");
        assertThat(saved.getValue().getHandledAt()).isNotNull();
        verify(notificationService).notify(any(NotificationCmd.class));
    }

    // ==================== 回执投递判据 ====================

    @Test
    @DisplayName("回执：提交人已登录（userId 非空）→ 投递（2026-10-01 拍板：消息中心为登录级能力，不再按邮箱认证过滤）")
    void receipt_loginOnly_delivers() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        service().reject(9L, handleReq(null, "已核实", "价格一致"));
        // 关键断言：口径已由「仅已认证用户」放宽为「登录即投递」
        verify(notificationService).notify(any(NotificationCmd.class));
    }

    @Test
    @DisplayName("回执：游客提交（userId=null）不投递 —— 纠错主路径刻意匿名，无归属可投")
    void receipt_guest_noNotify() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(null));
        service().reject(9L, handleReq(null, "已核实", "价格一致"));
        verify(notificationService, never()).notify(any());
    }

    @Test
    @DisplayName("回执：投递失败不影响处理结果（异常不外抛）")
    void receipt_failure_doesNotFailReject() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        org.mockito.Mockito.doThrow(new RuntimeException("notify down")).when(notificationService).notify(any());

        service().reject(9L, handleReq(null, "已核实", "价格一致"));
        verify(correctionMapper).updateById(any());
    }
}
