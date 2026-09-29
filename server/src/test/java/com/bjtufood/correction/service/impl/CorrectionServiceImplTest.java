package com.bjtufood.correction.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.canteen.dto.StallBriefVO;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.correction.constant.CorrectionConst;
import com.bjtufood.correction.dto.DishCorrectionAdoptReq;
import com.bjtufood.correction.dto.DishCorrectionHandleReq;
import com.bjtufood.correction.dto.DishCorrectionReq;
import com.bjtufood.correction.dto.StallConfirmVO;
import com.bjtufood.correction.entity.DishCorrection;
import com.bjtufood.correction.mapper.DishCorrectionMapper;
import com.bjtufood.dish.dto.DishCorrectionCmd;
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
 *   <li><b>回执投递判据</b>：游客（userId=null）与未认证账号一律不投递。</li>
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
        // 落库 Bean 用**真实实现**包裹 mock 的 mapper：事务边界收窄（机审移出事务）后，
        // 本类断言仍原样落在 correctionMapper.insert 上 —— 即「可见行为未变」的直接证据。
        return new CorrectionServiceImpl(correctionMapper, new CorrectionPersister(correctionMapper), dishService,
                stallService, userService, localSensitiveFilter, contentSecurityService, notificationService, imageUrlUtil);
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

    // ==================== adopt：幂等与两段式档口确认 ====================

    @Test
    @DisplayName("adopt：纠错不存在 → 400")
    void adopt_notFound_400() {
        when(correctionMapper.selectById(anyLong())).thenReturn(null);
        assertThatThrownBy(() -> service().adopt(9L, null))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
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

        StallConfirmVO result = service().adopt(9L, null);

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
        DishCorrectionAdoptReq req = new DishCorrectionAdoptReq();
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
        when(userService.isVerifiedById(1L)).thenReturn(true);

        assertThat(service().adopt(9L, null)).isNull();   // 命中即直接采纳，无需二次确认

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
    @DisplayName("adopt：写回返回 false（并发删除兜底）→ 4001 且不归档")
    void adopt_applyReturnsFalse_4001() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(dishService.existsById(anyLong())).thenReturn(true);
        when(stallService.findIdByName(any())).thenReturn(10L);
        when(stallService.getNameById(10L)).thenReturn("清真面档");
        when(dishService.applyCorrection(any())).thenReturn(false);

        assertThatThrownBy(() -> service().adopt(9L, null))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(correctionMapper, never()).updateById(any());
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
    @DisplayName("reject：纠错不存在 → 400")
    void reject_notFound_400() {
        when(correctionMapper.selectById(anyLong())).thenReturn(null);
        assertThatThrownBy(() -> service().reject(9L, handleReq(null, "已核实", "价格一致")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
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
    @DisplayName("reject：回复 / 不采纳原因 为空或纯空白 → 400")
    void reject_blankReplyOrReason_400() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));

        assertThatThrownBy(() -> service().reject(9L, handleReq(null, "  ", "价格一致")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> service().reject(9L, handleReq(null, "已核实", "   ")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(correctionMapper, never()).updateById(any());
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
        when(userService.isVerifiedById(1L)).thenReturn(true);

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
    @DisplayName("回执：游客提交（userId=null）不投递 —— 纠错主路径刻意匿名")
    void receipt_guest_noNotify() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(null));
        service().reject(9L, handleReq(null, "已核实", "价格一致"));
        verify(notificationService, never()).notify(any());
    }

    @Test
    @DisplayName("回执：提交人未邮箱认证 → 不投递")
    void receipt_unverified_noNotify() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(userService.isVerifiedById(1L)).thenReturn(false);
        service().reject(9L, handleReq(null, "已核实", "价格一致"));
        verify(notificationService, never()).notify(any());
    }

    @Test
    @DisplayName("回执：投递失败不影响处理结果（异常不外抛）")
    void receipt_failure_doesNotFailReject() {
        when(correctionMapper.selectById(anyLong())).thenReturn(pendingCorrection(1L));
        when(userService.isVerifiedById(1L)).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("notify down")).when(notificationService).notify(any());

        service().reject(9L, handleReq(null, "已核实", "价格一致"));
        verify(correctionMapper).updateById(any());
    }
}
