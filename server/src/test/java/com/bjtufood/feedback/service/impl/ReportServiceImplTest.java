package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.feedback.dto.ReportAdminVO;
import com.bjtufood.feedback.dto.ReportReq;
import com.bjtufood.feedback.entity.Report;
import com.bjtufood.feedback.mapper.ReportMapper;
import com.bjtufood.feedback.service.ReportOptionService;
import com.bjtufood.moderation.constant.ModerationConst;
import com.bjtufood.moderation.dto.ModerationHandleReq;
import com.bjtufood.moderation.dto.ModerationTaskVO;
import com.bjtufood.moderation.service.ModerationTaskService;
import com.bjtufood.review.dto.ReviewRelatedBriefVO;
import com.bjtufood.review.service.ReviewService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReportServiceImpl} 单元测试（评价举报链路）。
 * <p>
 * 聚焦四类退化后即为安全 / 数据 / 体验问题的逻辑：
 * <ol>
 *   <li><b>目标校验</b>：被举报评价不存在 / 不可见 → 4001；同一用户重复举报 → 400；</li>
 *   <li><b>原因白名单</b>：{@code optionId} 未命中启用字典 → 400 且不落库；</li>
 *   <li><b>提交两行同事务</b>：举报行 + 处置任务行（{@code status=pending}）；</li>
 *   <li><b>处置副作用</b>：仅「通过 + 勾选 + 仍待处置 + 有被举报评价」才隐藏评价，
 *       且回执文案必须写明「已隐藏该评价」（用户看不到那条评价了，不说明即为凭空消失）；
 *       「已处置后再提交」不得先隐藏再报错（副作用的执行顺序护栏）。</li>
 * </ol>
 * 被测类为纯 POJO：{@code @Transactional} 依赖 Spring 代理，单测中不生效，断言的是方法体内业务逻辑。
 */
class ReportServiceImplTest {

    /** 纯 Mockito 无 Spring 上下文，MyBatis-Plus 的 lambda 缓存需显式初始化 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), ReportServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, Report.class);
    }

    private final ReportMapper reportMapper = mock(ReportMapper.class);
    private final UserService userService = mock(UserService.class);
    private final ReportOptionService reportOptionService = mock(ReportOptionService.class);
    private final ModerationTaskService moderationTaskService = mock(ModerationTaskService.class);
    private final ReviewService reviewService = mock(ReviewService.class);

    private ReportServiceImpl service() {
        return new ReportServiceImpl(reportMapper, userService, reportOptionService,
                moderationTaskService, reviewService);
    }

    private static ReportReq reportReq(Long optionId) {
        ReportReq req = new ReportReq();
        req.setOptionId(optionId);
        return req;
    }

    private static ModerationTaskVO pendingTask() {
        ModerationTaskVO vo = new ModerationTaskVO();
        vo.setStatus(ModerationConst.STATUS_PENDING);
        return vo;
    }

    private static Report report(Long reviewId) {
        Report report = new Report();
        report.setId(4L);
        report.setUserId(9L);
        report.setReviewId(reviewId);
        report.setOptionId(1L);
        return report;
    }

    // ==================== submit ====================

    @Test
    @DisplayName("submit：被举报评价不存在 / 不可见 → 4001 且不落库")
    void submit_reviewMissing_4001() {
        when(reviewService.existsVisibleById(anyLong())).thenReturn(false);

        assertThatThrownBy(() -> service().submit(7L, 99L, reportReq(1L)))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(reportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submit：原因未命中启用字典（不存在 / 已停用）→ 400 且不落库")
    void submit_illegalOption_400() {
        when(reviewService.existsVisibleById(anyLong())).thenReturn(true);
        // 不加桩 ⇒ isSubmittable 恒 false

        assertThatThrownBy(() -> service().submit(7L, 99L, reportReq(999L)))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(reportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submit：同一登录用户重复举报 → 400 且不落库（去重按人 × 评价）")
    void submit_duplicate_400() {
        when(reviewService.existsVisibleById(anyLong())).thenReturn(true);
        when(reportOptionService.isSubmittable(anyLong())).thenReturn(true);
        when(reportMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().submit(7L, 99L, reportReq(1L)))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(reportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submit：正常落库 → 举报行（人 / 评价 / 原因）+ 同事务预建 pending 任务行")
    void submit_valid_persistsAndPreCreatesTask() {
        when(reviewService.existsVisibleById(anyLong())).thenReturn(true);
        when(reportOptionService.isSubmittable(anyLong())).thenReturn(true);
        when(reportMapper.selectCount(any())).thenReturn(0L);
        when(reportMapper.insert(any())).thenAnswer(inv -> {
            ((Report) inv.getArgument(0)).setId(4L);
            return 1;
        });

        service().submit(7L, 99L, reportReq(2L));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportMapper).insert(captor.capture());
        Report saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getReviewId()).isEqualTo(99L);
        assertThat(saved.getOptionId()).isEqualTo(2L);
        verify(moderationTaskService).create(ModerationConst.TYPE_REPORT, 4L);
    }

    @Test
    @DisplayName("submit：无登录态（userId=null）→ 401（report.user_id 必填真实登录用户），不落库")
    void submit_guest_401() {
        assertThatThrownBy(() -> service().submit(null, 99L, reportReq(1L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(401));

        verify(reportMapper, never()).insert(any());
    }

    // ==================== 管理端列表 ====================

    @Test
    @DisplayName("listForAdmin：补齐举报人昵称 / 原因中文名 / 被举报评价摘要 / 处置结果")
    void listForAdmin_fillsNamesBriefAndTask() {
        Page<Report> page = new Page<>(1, 10);
        page.setRecords(List.of(report(30L)));
        page.setTotal(1);
        when(reportMapper.selectPage(any(), any())).thenReturn(page);
        when(userService.mapNameByIds(any())).thenReturn(Map.of(9L, "举报小能手"));
        when(reportOptionService.nameByIdAll()).thenReturn(Map.of(1L, "垃圾广告 / 营销刷屏"));
        ReviewRelatedBriefVO brief = new ReviewRelatedBriefVO();
        brief.setContent("刷屏广告");
        brief.setDishName("牛肉拉面");
        brief.setHidden(true);
        when(reviewService.mapRelatedBriefByIds(any())).thenReturn(Map.of(30L, brief));
        ModerationTaskVO task = new ModerationTaskVO();
        task.setStatus(ModerationConst.STATUS_HANDLED);
        task.setOutcome(ModerationConst.OUTCOME_HANDLED);
        task.setReplyTitle("举报已受理");
        task.setReplyContent("已隐藏该评价");
        when(moderationTaskService.mapByTypeAndIds(eq(ModerationConst.TYPE_REPORT), any()))
                .thenReturn(Map.of(4L, task));

        ReportAdminVO vo = service().listForAdmin(null, null, 1, 10).getRecords().get(0);

        assertThat(vo.getUserName()).isEqualTo("举报小能手");
        assertThat(vo.getOptionName()).isEqualTo("垃圾广告 / 营销刷屏");
        assertThat(vo.getReviewContent()).isEqualTo("刷屏广告");
        assertThat(vo.getReviewDishName()).isEqualTo("牛肉拉面");
        assertThat(vo.getReviewHidden()).isTrue();
        assertThat(vo.getReviewDeleted()).isFalse();
        assertThat(vo.getReplyTitle()).isEqualTo("举报已受理");
        assertThat(vo.getStatus()).isEqualTo(ModerationConst.STATUS_HANDLED);
    }

    @Test
    @DisplayName("listForAdmin：被举报评价已删除（review_id=NULL）→ reviewDeleted=true，摘要不查不填")
    void listForAdmin_deletedReview_isFlagged() {
        Report deletedTarget = report(null);
        Page<Report> page = new Page<>(1, 10);
        page.setRecords(List.of(deletedTarget));
        page.setTotal(1);
        when(reportMapper.selectPage(any(), any())).thenReturn(page);
        when(userService.mapNameByIds(any())).thenReturn(Map.of());
        when(reportOptionService.nameByIdAll()).thenReturn(Map.of());
        when(reviewService.mapRelatedBriefByIds(any())).thenReturn(Map.of());
        when(moderationTaskService.mapByTypeAndIds(any(), any())).thenReturn(Map.of());

        ReportAdminVO vo = service().listForAdmin(null, null, 1, 10).getRecords().get(0);

        assertThat(vo.getReviewDeleted()).isTrue();
        assertThat(vo.getReviewId()).isNull();
        assertThat(vo.getReviewContent()).isNull();
        assertThat(vo.getReviewHidden()).isNull();
    }

    // ==================== 处置：联动隐藏与文案 ====================

    @Test
    @DisplayName("handle：举报不存在 → 4001，且不触达处置任务服务")
    void handle_notFound_4001() {
        when(reportMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> service().handle(4L, new ModerationHandleReq(), 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(moderationTaskService, never()).handle(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handle：通过 + 勾选隐藏 → 隐藏被举报评价，回执文案写明「已隐藏该评价」")
    void handle_handledWithHide_hidesReviewAndExplains() {
        when(reportMapper.selectById(4L)).thenReturn(report(30L));
        when(moderationTaskService.findByTypeAndId(ModerationConst.TYPE_REPORT, 4L)).thenReturn(pendingTask());
        when(reviewService.hideIfVisible(30L)).thenReturn(true);
        ModerationHandleReq req = new ModerationHandleReq();
        req.setHideReview(true);
        req.setReplyContent("举报属实");

        service().handle(4L, req, 3L);

        verify(reviewService).hideIfVisible(30L);
        verify(moderationTaskService).handle(ModerationConst.TYPE_REPORT, 4L, ModerationConst.OUTCOME_HANDLED,
                null, "举报属实（已隐藏该评价）", 3L, 9L);
    }

    @Test
    @DisplayName("handle：未勾选隐藏 → 不动评价，文案原样下发")
    void handle_withoutHide_doesNotTouchReview() {
        when(reportMapper.selectById(4L)).thenReturn(report(30L));
        when(moderationTaskService.findByTypeAndId(ModerationConst.TYPE_REPORT, 4L)).thenReturn(pendingTask());
        ModerationHandleReq req = new ModerationHandleReq();
        req.setReplyContent("举报属实");

        service().handle(4L, req, 3L);

        verify(reviewService, never()).hideIfVisible(any());
        verify(moderationTaskService).handle(ModerationConst.TYPE_REPORT, 4L, ModerationConst.OUTCOME_HANDLED,
                null, "举报属实", 3L, 9L);
    }

    @Test
    @DisplayName("handle：不采纳 → 不消费 hideReview（评价不动）")
    void handle_rejected_ignoresHideFlag() {
        when(reportMapper.selectById(4L)).thenReturn(report(30L));
        when(moderationTaskService.findByTypeAndId(ModerationConst.TYPE_REPORT, 4L)).thenReturn(pendingTask());
        ModerationHandleReq req = new ModerationHandleReq();
        req.setOutcome(ModerationConst.OUTCOME_REJECTED);
        req.setHideReview(true);
        req.setReplyContent("经核实不构成违规");

        service().handle(4L, req, 3L);

        verify(reviewService, never()).hideIfVisible(any());
        verify(moderationTaskService).handle(ModerationConst.TYPE_REPORT, 4L, ModerationConst.OUTCOME_REJECTED,
                null, "经核实不构成违规", 3L, 9L);
    }

    @Test
    @DisplayName("handle：已处置后再提交（勾选隐藏）→ 不隐藏评价（副作用不得先于校验执行）")
    void handle_alreadyHandled_doesNotHideReviewFirst() {
        when(reportMapper.selectById(4L)).thenReturn(report(30L));
        ModerationTaskVO handled = new ModerationTaskVO();
        handled.setStatus(ModerationConst.STATUS_HANDLED);
        when(moderationTaskService.findByTypeAndId(ModerationConst.TYPE_REPORT, 4L)).thenReturn(handled);
        ModerationHandleReq req = new ModerationHandleReq();
        req.setHideReview(true);

        service().handle(4L, req, 3L);

        verify(reviewService, never()).hideIfVisible(any());
        // 真正的防重复结论仍由处置任务服务在事务内强制
        verify(moderationTaskService).handle(eq(ModerationConst.TYPE_REPORT), eq(4L), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handle：被举报评价已删除（review_id=NULL）→ 不尝试隐藏（无从隐藏，静默成功）")
    void handle_deletedReview_skipsHide() {
        when(reportMapper.selectById(4L)).thenReturn(report(null));
        when(moderationTaskService.findByTypeAndId(ModerationConst.TYPE_REPORT, 4L)).thenReturn(pendingTask());
        ModerationHandleReq req = new ModerationHandleReq();
        req.setHideReview(true);

        service().handle(4L, req, 3L);

        verify(reviewService, never()).hideIfVisible(any());
    }
}
