package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.feedback.constant.FeedbackConst;
import com.bjtufood.feedback.dto.FeedbackHandleReq;
import com.bjtufood.feedback.dto.FeedbackReq;
import com.bjtufood.feedback.dto.ReportReq;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.feedback.service.ReportReasonService;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
import com.bjtufood.notification.dto.NotificationCmd;
import com.bjtufood.notification.service.NotificationService;
import com.bjtufood.review.service.ReviewService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FeedbackServiceImpl} 单元测试。
 * <p>
 * 目的：在「把微信机审移出事务边界」的重构之前，先把当前行为**钉死**，
 * 使重构后能直接复用本类断言「可见行为未变」（落库字段值、错误码、回执投递判据）。
 * <p>
 * 聚焦四类最易被改坏、且退化后即为安全 / 数据问题的逻辑：
 * <ol>
 *   <li><b>写入口类型白名单</b>（{@code submit}）：仅 {@code bug/suggestion/other} 可写；
 *       举报 / 纠错已迁出独立端点，若放宽为「原样落库」会让拆分后的写入口重新混流；</li>
 *   <li><b>举报目标存在性与去重</b>（{@code report}）：不存在须 4001（端上据此给恢复路径），
 *       同一登录用户重复举报须 400；游客因无身份标识**不去重**；</li>
 *   <li><b>处理结论与不采纳原因</b>（{@code handle}）：{@code outcome} 白名单、
 *       {@code rejected ⇒ rejectReason 非空且 ≤200 字}、回复必填；</li>
 *   <li><b>回执投递判据</b>：<b>登录级</b>——userId 非空即投递（2026-10-01 拍板放宽：
 *       消息中心是登录级能力，游客提交的反馈也应收到处理结果，此前「仅已认证用户投递」
 *       会让游客的消息中心永久空转）；游客（userId=null）不投递（无归属可投）。</li>
 * </ol>
 * 被测类为纯 POJO：{@code @Transactional} 依赖 Spring 代理，单测中不生效，断言的是方法体内业务逻辑。
 */
class FeedbackServiceImplTest {

    /** 与 {@link com.bjtufood.review.service.impl.ReviewServiceImplTest} 同因：纯 Mockito 无 Spring 上下文，
     *  MyBatis-Plus 的 lambda 缓存需显式初始化，否则构造 {@code LambdaQueryWrapper} 会抛异常。 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), FeedbackServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, Feedback.class);
    }

    private final FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
    private final UserService userService = mock(UserService.class);
    private final DishService dishService = mock(DishService.class);
    private final LocalSensitiveFilter localSensitiveFilter = mock(LocalSensitiveFilter.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ContentSecurityService contentSecurityService = mock(ContentSecurityService.class);
    private final ImageUrlUtil imageUrlUtil = mock(ImageUrlUtil.class);
    private final ReviewService reviewService = mock(ReviewService.class);
    /** 举报原因字典真源（A7 落地后为 `report_reason` 表；原为 `FeedbackConst` 常量） */
    private final ReportReasonService reportReasonService = mock(ReportReasonService.class);

    /**
     * 构造器参数顺序须与 {@code FeedbackServiceImpl} 的 final 字段声明顺序逐字一致（@RequiredArgsConstructor）。
     * <p>
     * 落库 Bean 用**真实实现**包裹 mock 的 mapper：事务边界收窄（机审移出事务）后，
     * 本类断言仍原样落在 {@code feedbackMapper.insert} 上 —— 这正是「可见行为未变」的直接证据。
     */
    private FeedbackServiceImpl service() {
        return new FeedbackServiceImpl(feedbackMapper, new FeedbackPersister(feedbackMapper), userService, dishService,
                localSensitiveFilter, notificationService, reportReasonService, contentSecurityService, imageUrlUtil,
                reviewService);
    }

    /** 本地词库默认原样返回（不脱敏），便于断言落库值 */
    private void stubFilterPassThrough() {
        when(localSensitiveFilter.filter(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * 举报原因可提交（真源为 `report_reason` 表：存在且**启用**）。
     * <p>
     * 此处直接桩定 —— 表驱动逻辑（上/下限、引用计数、停用）由 `ReportReasonServiceImpl` 的单测覆盖；
     * 本类只关心「不可提交时 400 且不落库」这一条契约。
     */
    private void stubReasonSubmittable() {
        when(reportReasonService.isSubmittable(anyString())).thenReturn(true);
    }

    private FeedbackReq feedbackReq(String type, String content) {
        FeedbackReq req = new FeedbackReq();
        req.setType(type);
        req.setContent(content);
        return req;
    }

    private ReportReq reportReq(String reason, String content) {
        ReportReq req = new ReportReq();
        req.setReason(reason);
        req.setContent(content);
        return req;
    }

    // ==================== submit：写入口类型白名单 ====================

    @Test
    @DisplayName("submit：合法类型落库 —— 二级分类与关联对象恒空（举报/纠错已迁出）")
    void submit_writableType_persistsWithNullSubAndRelated() {
        stubFilterPassThrough();

        service().submit(7L, feedbackReq(FeedbackConst.TYPE_BUG, "首页偶发白屏"));

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackMapper).insert(captor.capture());
        Feedback saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getType()).isEqualTo(FeedbackConst.TYPE_BUG);
        assertThat(saved.getContent()).isEqualTo("首页偶发白屏");
        // 纯反馈：无二级分类、无关联对象（历史列保持 NULL）
        assertThat(saved.getSub()).isNull();
        assertThat(saved.getRelatedType()).isNull();
        assertThat(saved.getRelatedId()).isNull();
        assertThat(saved.getStatus()).isEqualTo(FeedbackConst.STATUS_PENDING);
    }

    @Test
    @DisplayName("submit：举报与历史遗留类型一律 400 且不落库（含 report / issue / add / error）")
    void submit_illegalType_rejected400AndNotPersisted() {
        stubFilterPassThrough();
        for (String illegal : new String[]{
                FeedbackConst.TYPE_REPORT, FeedbackConst.TYPE_ISSUE, FeedbackConst.TYPE_ADD, FeedbackConst.TYPE_ERROR}) {
            assertThatThrownBy(() -> service().submit(7L, feedbackReq(illegal, "内容")))
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        }
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submit：内容为空 / 纯空白 → 400 且不落库")
    void submit_blankContent_rejected400AndNotPersisted() {
        stubFilterPassThrough();
        assertThatThrownBy(() -> service().submit(7L, feedbackReq(FeedbackConst.TYPE_BUG, "   ")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(feedbackMapper, never()).insert(any());
    }

    // ==================== report：目标存在性 / 原因白名单 / 去重 ====================

    @Test
    @DisplayName("report：目标评价不存在（含已隐藏/已删除）→ 4001 且不落库")
    void report_targetMissing_4001AndNotPersisted() {
        stubFilterPassThrough();
        when(reviewService.existsVisibleById(anyLong())).thenReturn(false);

        assertThatThrownBy(() -> service().report(7L, 99L, reportReq("spam", "")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    @DisplayName("report：举报原因缺失 / 不在字典（含已停用）→ 400 且不落库")
    void report_illegalReason_rejected400AndNotPersisted() {
        stubFilterPassThrough();
        when(reviewService.existsVisibleById(anyLong())).thenReturn(true);
        // 不加桩 ⇒ isSubmittable 返回 false，等价于「原因不存在或已停用」

        assertThatThrownBy(() -> service().report(7L, 99L, reportReq(null, "")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> service().report(7L, 99L, reportReq("not-a-reason", "")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    @DisplayName("report：同一登录用户重复举报 → 400 且不落库")
    void report_duplicate_rejected400AndNotPersisted() {
        stubFilterPassThrough();
        when(reviewService.existsVisibleById(anyLong())).thenReturn(true);
        when(feedbackMapper.selectCount(any())).thenReturn(1L);
        stubReasonSubmittable();

        assertThatThrownBy(() -> service().report(7L, 99L, reportReq("spam", "")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    @DisplayName("report：正常落库 —— sub=原因、关联类型=review、关联 id=路径 id")
    void report_valid_persistsWithSubAndRelated() {
        stubFilterPassThrough();
        when(reviewService.existsVisibleById(anyLong())).thenReturn(true);
        when(feedbackMapper.selectCount(any())).thenReturn(0L);
        stubReasonSubmittable();

        service().report(7L, 99L, reportReq("abuse", "辱骂内容"));

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackMapper).insert(captor.capture());
        Feedback saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getType()).isEqualTo(FeedbackConst.TYPE_REPORT);
        assertThat(saved.getSub()).isEqualTo("abuse");
        assertThat(saved.getRelatedType()).isEqualTo(FeedbackConst.RELATED_REVIEW);
        assertThat(saved.getRelatedId()).isEqualTo(99L);
        assertThat(saved.getStatus()).isEqualTo(FeedbackConst.STATUS_PENDING);
    }

    @Test
    @DisplayName("report：游客（userId=null）无身份标识 → 跳过去重查询，直接落库")
    void report_guest_skipsDuplicateCheck() {
        stubFilterPassThrough();
        when(reviewService.existsVisibleById(anyLong())).thenReturn(true);
        stubReasonSubmittable();

        service().report(null, 99L, reportReq("spam", ""));

        // 游客无 userId 可判重 ⇒ 不发起 selectCount，但仍要落一条匿名举报
        verify(feedbackMapper, never()).selectCount(any());
        verify(feedbackMapper).insert(any());
    }

    // ==================== handle：处理结论与不采纳原因 ====================

    private Feedback pendingFeedback(Long userId) {
        Feedback f = new Feedback();
        f.setId(5L);
        f.setUserId(userId);
        f.setStatus(FeedbackConst.STATUS_PENDING);
        return f;
    }

    @Test
    @DisplayName("handle：反馈不存在 → 4001（目标态契约，见 docs/api/web/feedback.md）")
    void handle_notFound_4001() {
        when(feedbackMapper.selectById(anyLong())).thenReturn(null);
        FeedbackHandleReq req = new FeedbackHandleReq();
        req.setReply("已处理");

        assertThatThrownBy(() -> service().handle(5L, req))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(feedbackMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("handle：回复可留空（B2 起可选）→ 正常处理；回执正文退化为固定文案，不产生空通知")
    void handle_blankReply_allowed() {
        when(feedbackMapper.selectById(anyLong())).thenReturn(pendingFeedback(1L));
        for (String blank : new String[]{null, "", "   "}) {
            // 每次都要一条**新的 pending 记录**：处理过后再处理会 400「该记录已处理」
            when(feedbackMapper.selectById(anyLong())).thenReturn(pendingFeedback(1L));
            FeedbackHandleReq req = new FeedbackHandleReq();
            req.setReply(blank);
            service().handle(5L, req);
        }
        ArgumentCaptor<NotificationCmd> cmd = ArgumentCaptor.forClass(NotificationCmd.class);
        verify(notificationService, times(3)).notify(cmd.capture());
        // 关键：回执正文**始终有可读内容**，不得出现「：」后为空的空通知
        for (NotificationCmd c : cmd.getAllValues()) {
            assertThat(c.getContent()).isNotBlank();
            assertThat(c.getContent()).doesNotEndWith("：");
        }
    }

    @Test
    @DisplayName("handle：rejected 结论下不采纳原因缺失 / 纯空白 → 400（结论必填项不变）")
    void handle_blankRejectReason_400() {
        when(feedbackMapper.selectById(anyLong())).thenReturn(pendingFeedback(1L));
        FeedbackHandleReq req = new FeedbackHandleReq();
        req.setOutcome("rejected");
        req.setRejectReason("   ");
        assertThatThrownBy(() -> service().handle(5L, req))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(feedbackMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("handle：处理结论非法 → 400（缺省为 handled）")
    void handle_illegalOutcome_400() {
        when(feedbackMapper.selectById(anyLong())).thenReturn(pendingFeedback(1L));
        FeedbackHandleReq req = new FeedbackHandleReq();
        req.setReply("已处理");
        req.setOutcome("whatever");

        assertThatThrownBy(() -> service().handle(5L, req))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(feedbackMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("handle：结论为 rejected 但未填不采纳原因 → 400")
    void handle_rejectedWithoutReason_400() {
        when(feedbackMapper.selectById(anyLong())).thenReturn(pendingFeedback(1L));
        FeedbackHandleReq req = new FeedbackHandleReq();
        req.setReply("已处理");
        req.setOutcome(FeedbackConst.OUTCOME_REJECTED);
        req.setRejectReason("   ");

        assertThatThrownBy(() -> service().handle(5L, req))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(feedbackMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("handle：不采纳原因超过 200 字 → 400")
    void handle_rejectedReasonTooLong_400() {
        when(feedbackMapper.selectById(anyLong())).thenReturn(pendingFeedback(1L));
        FeedbackHandleReq req = new FeedbackHandleReq();
        req.setReply("已处理");
        req.setOutcome(FeedbackConst.OUTCOME_REJECTED);
        req.setRejectReason("不".repeat(FeedbackConst.REJECT_REASON_MAX_LENGTH + 1));

        assertThatThrownBy(() -> service().handle(5L, req))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(feedbackMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("handle：正常处理 → 落库 handled + 回复 trim + 结论派生依据（rejectReason 为 NULL）+ 投递回执")
    void handle_handled_persistsAndNotifies() {
        when(feedbackMapper.selectById(anyLong())).thenReturn(pendingFeedback(1L));

        FeedbackHandleReq req = new FeedbackHandleReq();
        req.setReply("  已处理，感谢反馈  ");
        service().handle(5L, req);

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackMapper).updateById(captor.capture());
        Feedback updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo(FeedbackConst.STATUS_HANDLED);
        assertThat(updated.getReply()).isEqualTo("已处理，感谢反馈");
        // 结论 handled ⇒ rejectReason 恒 NULL（VO 的 outcome 由 status + rejectReason 派生）
        assertThat(updated.getRejectReason()).isNull();
        assertThat(updated.getHandledAt()).isNotNull();

        ArgumentCaptor<NotificationCmd> cmd = ArgumentCaptor.forClass(NotificationCmd.class);
        verify(notificationService).notify(cmd.capture());
        assertThat(cmd.getValue().getTitle()).isEqualTo("反馈已处理");
    }

    @Test
    @DisplayName("handle：提交人已登录（userId 非空）→ 投递回执（2026-10-01 拍板：消息中心为登录级能力，不再按邮箱认证过滤）")
    void handle_loginOnly_deliversReceipt() {
        when(feedbackMapper.selectById(anyLong())).thenReturn(pendingFeedback(1L));

        FeedbackHandleReq req = new FeedbackHandleReq();
        req.setReply("已处理");
        service().handle(5L, req);

        // 关键断言：口径已由「仅已认证用户」放宽为「登录即投递」——
        // 此前游客的消息中心会永久空转（提交的反馈永远收不到处理结果）。
        verify(notificationService).notify(any());
    }

    @Test
    @DisplayName("handle：游客提交（userId=null）不投递回执（反馈主路径刻意匿名，无归属可投）")
    void handle_guest_noReceipt() {
        when(feedbackMapper.selectById(anyLong())).thenReturn(pendingFeedback(null));

        FeedbackHandleReq req = new FeedbackHandleReq();
        req.setReply("已处理");
        service().handle(5L, req);

        verify(notificationService, never()).notify(any());
    }

    @Test
    @DisplayName("handle：回执投递失败不影响处理结果（异常不外抛）")
    void handle_notifyFailure_doesNotFailHandle() {
        when(feedbackMapper.selectById(anyLong())).thenReturn(pendingFeedback(1L));
        org.mockito.Mockito.doThrow(new RuntimeException("notify down"))
                .when(notificationService).notify(any());

        FeedbackHandleReq req = new FeedbackHandleReq();
        req.setReply("已处理");
        service().handle(5L, req);

        verify(feedbackMapper).updateById(any());
    }

    // ==================== 机审：游客与有 openid 路径 ====================

    @Test
    @DisplayName("submit：机审取 openid 走 msgSecCheck（scene=2）；无 openid 账号亦照常调用（由服务内部跳过）")
    void submit_withOpenid_callsSecCheckWithScene2() {
        stubFilterPassThrough();
        UserAuthContextVO user = new UserAuthContextVO();
        user.setOpenid("oXxx");
        when(userService.getAuthContext(7L)).thenReturn(user);

        service().submit(7L, feedbackReq(FeedbackConst.TYPE_SUGGESTION, "希望增加素食档口"));

        verify(contentSecurityService).checkText("oXxx", "希望增加素食档口", 2);
    }

    @Test
    @DisplayName("submit：游客（userId=null）机审仍走本地词库后落库（不因无身份而拒绝）")
    void submit_guest_persists() {
        stubFilterPassThrough();
        when(userService.getAuthContext(null)).thenReturn(null);

        service().submit(null, feedbackReq(FeedbackConst.TYPE_OTHER, "匿名反馈"));

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
        verify(contentSecurityService).checkText(null, "匿名反馈", 2);
    }

    // ==================== 账号归属迁移 ====================

    @Test
    @DisplayName("migrateOwnership：空参 / 同一用户 → 返回 0 且不发起更新")
    void migrateOwnership_noopCases() {
        assertThat(service().migrateOwnership(null, 2L)).isZero();
        assertThat(service().migrateOwnership(1L, null)).isZero();
        assertThat(service().migrateOwnership(1L, 1L)).isZero();
        verify(feedbackMapper, never()).update(any(), any());
    }
}
