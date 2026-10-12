package com.bjtufood.moderation.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.auth.service.AdminAccountService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.moderation.constant.ModerationConst;
import com.bjtufood.moderation.dto.ModerationTaskVO;
import com.bjtufood.moderation.entity.ModerationTask;
import com.bjtufood.moderation.mapper.ModerationTaskMapper;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ModerationTaskServiceImpl} 单元测试（三类工单共用的处置状态机）。
 * <p>
 * 聚焦四类退化后即为「数据 / 体验」问题的逻辑：
 * <ol>
 *   <li><b>预建</b>：提交事务内写入的行必须是 {@code status=pending}、结论与文案为空
 *       （否则管理端「待处置」列表直接漏行）；</li>
 *   <li><b>处置结论与文案</b>：{@code outcome} 白名单、{@code rejected ⇒ 文案必填}、
 *       留空按类型补缺省文案（回执正文不得为空）；</li>
 *   <li><b>幂等护栏</b>：已处置再处置 → 400 且<b>不重复投递回执</b>；任务行缺失 → 4001；</li>
 *   <li><b>回执投递</b>：与任务行同一套文案（快照），收件人为空时不投递但归档照常。</li>
 * </ol>
 * 被测类为纯 POJO：{@code @Transactional} 依赖 Spring 代理，单测中不生效，断言的是方法体内业务逻辑。
 */
class ModerationTaskServiceImplTest {

    /** 纯 Mockito 无 Spring 上下文，MyBatis-Plus 的 lambda 缓存需显式初始化 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(),
                ModerationTaskServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, ModerationTask.class);
    }

    private final ModerationTaskMapper moderationTaskMapper = mock(ModerationTaskMapper.class);
    private final AdminAccountService adminAccountService = mock(AdminAccountService.class);
    private final NotificationService notificationService = mock(NotificationService.class);

    private ModerationTaskServiceImpl service() {
        return new ModerationTaskServiceImpl(moderationTaskMapper, adminAccountService, notificationService);
    }

    private static ModerationTask task(String status) {
        ModerationTask task = new ModerationTask();
        task.setId(7L);
        task.setType(ModerationConst.TYPE_FEEDBACK);
        task.setTypeId(5L);
        task.setStatus(status);
        return task;
    }

    // ==================== create：提交事务内预建 ====================

    @Test
    @DisplayName("create：写入 pending 行，结论与文案保持 NULL（未处置即无结论）")
    void create_writesPendingRow() {
        service().create(ModerationConst.TYPE_FEEDBACK, 5L);

        ArgumentCaptor<ModerationTask> captor = ArgumentCaptor.forClass(ModerationTask.class);
        verify(moderationTaskMapper).insert(captor.capture());
        ModerationTask saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(ModerationConst.TYPE_FEEDBACK);
        assertThat(saved.getTypeId()).isEqualTo(5L);
        assertThat(saved.getStatus()).isEqualTo(ModerationConst.STATUS_PENDING);
        assertThat(saved.getOutcome()).isNull();
        assertThat(saved.getReplyTitle()).isNull();
        assertThat(saved.getReplyContent()).isNull();
        assertThat(saved.getHandledBy()).isNull();
        assertThat(saved.getHandledAt()).isNull();
    }

    @Test
    @DisplayName("create：工单类型非法 / ID 缺失 → 400 且不落库（多态键必须成对且可识别）")
    void create_illegalTypeOrId_rejected() {
        assertThatThrownBy(() -> service().create("unknown", 5L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> service().create(ModerationConst.TYPE_FEEDBACK, null))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(moderationTaskMapper, never()).insert(any());
    }

    // ==================== handle：结论与文案 ====================

    @Test
    @DisplayName("handle：不采纳但文案留空 / 纯空白 → 400「请填写不采纳原因」，不写库不投递")
    void handle_rejectedWithoutContent_400() {
        when(moderationTaskMapper.selectOne(any())).thenReturn(task(ModerationConst.STATUS_PENDING));

        for (String blank : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> service().handle(ModerationConst.TYPE_FEEDBACK, 5L,
                    ModerationConst.OUTCOME_REJECTED, null, blank, 1L, 9L))
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        }
        verify(moderationTaskMapper, never()).update(any(), any());
        verify(notificationService, never()).notifyNow(any());
    }

    @Test
    @DisplayName("handle：处置结论非法 → 400（缺省 handled）")
    void handle_illegalOutcome_400() {
        when(moderationTaskMapper.selectOne(any())).thenReturn(task(ModerationConst.STATUS_PENDING));

        assertThatThrownBy(() -> service().handle(ModerationConst.TYPE_FEEDBACK, 5L,
                "whatever", null, "文案", 1L, 9L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(moderationTaskMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("handle：通过且文案留空 → 补缺省文案（回执必须可读），标题/正文快照与任务行同一份")
    void handle_handled_blankTextFallsBackToDefaults() {
        when(moderationTaskMapper.selectOne(any())).thenReturn(task(ModerationConst.STATUS_PENDING));
        when(moderationTaskMapper.update(any(ModerationTask.class), any())).thenReturn(1);

        service().handle(ModerationConst.TYPE_FEEDBACK, 5L, null, "  ", null, 3L, 9L);

        ArgumentCaptor<ModerationTask> saved = ArgumentCaptor.forClass(ModerationTask.class);
        verify(moderationTaskMapper).update(saved.capture(), any());
        ModerationTask updated = saved.getValue();
        assertThat(updated.getStatus()).isEqualTo(ModerationConst.STATUS_HANDLED);
        assertThat(updated.getOutcome()).isEqualTo(ModerationConst.OUTCOME_HANDLED);
        assertThat(updated.getReplyTitle()).isEqualTo("反馈已处理");
        assertThat(updated.getReplyContent()).isEqualTo("你提交的反馈已处理。");
        assertThat(updated.getHandledBy()).isEqualTo(3L);
        assertThat(updated.getHandledAt()).isNotNull();

        ArgumentCaptor<NotificationCmd> cmd = ArgumentCaptor.forClass(NotificationCmd.class);
        verify(notificationService).notifyNow(cmd.capture());
        assertThat(cmd.getValue().getUserId()).isEqualTo(9L);
        assertThat(cmd.getValue().getReplyTitle()).isEqualTo(updated.getReplyTitle());
        assertThat(cmd.getValue().getReplyContent()).isEqualTo(updated.getReplyContent());
    }

    @Test
    @DisplayName("handle：type=report 的缺省文案与 feedback 不同（回执按工单类型分流，不得串文案）")
    void handle_defaultsAreTypeSpecific() {
        ModerationTask report = task(ModerationConst.STATUS_PENDING);
        report.setType(ModerationConst.TYPE_REPORT);
        when(moderationTaskMapper.selectOne(any())).thenReturn(report);
        when(moderationTaskMapper.update(any(ModerationTask.class), any())).thenReturn(1);

        service().handle(ModerationConst.TYPE_REPORT, 5L, ModerationConst.OUTCOME_HANDLED,
                null, "已受理并隐藏该评价", 3L, 9L);

        ArgumentCaptor<ModerationTask> saved = ArgumentCaptor.forClass(ModerationTask.class);
        verify(moderationTaskMapper).update(saved.capture(), any());
        assertThat(saved.getValue().getReplyTitle()).isEqualTo("举报已受理");
        assertThat(saved.getValue().getReplyContent()).isEqualTo("已受理并隐藏该评价");
    }

    @Test
    @DisplayName("handle：标题 / 正文超长 → 400 且不写库（列宽口径由本域强制）")
    void handle_tooLongText_400() {
        when(moderationTaskMapper.selectOne(any())).thenReturn(task(ModerationConst.STATUS_PENDING));

        assertThatThrownBy(() -> service().handle(ModerationConst.TYPE_FEEDBACK, 5L,
                ModerationConst.OUTCOME_HANDLED, "题".repeat(ModerationConst.REPLY_TITLE_MAX_LENGTH + 1),
                "文", 1L, 9L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> service().handle(ModerationConst.TYPE_FEEDBACK, 5L,
                ModerationConst.OUTCOME_HANDLED, null,
                "文".repeat(ModerationConst.REPLY_CONTENT_MAX_LENGTH + 1), 1L, 9L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(moderationTaskMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("handle：任务行缺失 → 4001（数据不一致不得静默通过）")
    void handle_taskMissing_4001() {
        when(moderationTaskMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service().handle(ModerationConst.TYPE_FEEDBACK, 5L,
                ModerationConst.OUTCOME_HANDLED, null, "文案", 1L, 9L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(moderationTaskMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("handle：已处置再处置 → 400，且**不重复投递回执**")
    void handle_alreadyHandled_400WithoutSecondReceipt() {
        when(moderationTaskMapper.selectOne(any())).thenReturn(task(ModerationConst.STATUS_HANDLED));

        assertThatThrownBy(() -> service().handle(ModerationConst.TYPE_FEEDBACK, 5L,
                ModerationConst.OUTCOME_HANDLED, null, "文案", 1L, 9L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(moderationTaskMapper, never()).update(any(), any());
        verify(notificationService, never()).notifyNow(any());
    }

    @Test
    @DisplayName("handle：并发窗口内被他人抢先处置 → UPDATE 落空 → 400 且不投递回执（判据在 WHERE，不靠预检）")
    void handle_racedByAnotherAdmin_updateMissesWithoutReceipt() {
        when(moderationTaskMapper.selectOne(any())).thenReturn(task(ModerationConst.STATUS_PENDING));
        // 读到 pending，但 UPDATE 的 status=pending 条件已落空（他人抢先归档）
        when(moderationTaskMapper.update(any(ModerationTask.class), any())).thenReturn(0);

        assertThatThrownBy(() -> service().handle(ModerationConst.TYPE_FEEDBACK, 5L,
                ModerationConst.OUTCOME_HANDLED, null, "文案", 1L, 9L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(notificationService, never()).notifyNow(any());
    }

    @Test
    @DisplayName("handle：收件人为空 → 归档照常但不投递（兜底分支：无归属即无可投对象）")
    void handle_withoutRecipient_archivesWithoutReceipt() {
        when(moderationTaskMapper.selectOne(any())).thenReturn(task(ModerationConst.STATUS_PENDING));
        when(moderationTaskMapper.update(any(ModerationTask.class), any())).thenReturn(1);

        service().handle(ModerationConst.TYPE_FEEDBACK, 5L, ModerationConst.OUTCOME_HANDLED,
                null, "文案", 1L, null);

        verify(moderationTaskMapper).update(any(), any());
        verify(notificationService, never()).notifyNow(any());
    }

    // ==================== 批量回填 / 计数 / 单条判据 ====================

    @Test
    @DisplayName("mapByTypeAndIds：空集合 → 空 Map 且不查库（省掉无意义的 IN ()）")
    void mapByTypeAndIds_emptyInputShortCircuits() {
        assertThat(service().mapByTypeAndIds(ModerationConst.TYPE_FEEDBACK, null)).isEmpty();
        assertThat(service().mapByTypeAndIds(ModerationConst.TYPE_FEEDBACK, List.of())).isEmpty();
        verify(moderationTaskMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("mapByTypeAndIds：批量回填处置结果与处置人登录名（一次 IN 查询 + 一次账号名投影）")
    void mapByTypeAndIds_fillsResultsAndHandlerName() {
        ModerationTask handled = task(ModerationConst.STATUS_HANDLED);
        handled.setTypeId(5L);
        handled.setOutcome(ModerationConst.OUTCOME_REJECTED);
        handled.setReplyTitle("反馈未采纳");
        handled.setReplyContent("该问题已在下个版本修复");
        handled.setHandledBy(3L);
        handled.setHandledAt(java.time.LocalDateTime.of(2026, 10, 7, 9, 30, 15));
        when(moderationTaskMapper.selectList(any())).thenReturn(List.of(handled));
        when(adminAccountService.mapUsernameByIds(any())).thenReturn(Map.of(3L, "kingdo404"));

        Map<Long, ModerationTaskVO> result =
                service().mapByTypeAndIds(ModerationConst.TYPE_FEEDBACK, List.of(5L));

        ModerationTaskVO vo = result.get(5L);
        assertThat(vo).isNotNull();
        assertThat(vo.getStatus()).isEqualTo(ModerationConst.STATUS_HANDLED);
        assertThat(vo.getOutcome()).isEqualTo(ModerationConst.OUTCOME_REJECTED);
        assertThat(vo.getReplyTitle()).isEqualTo("反馈未采纳");
        assertThat(vo.getReplyContent()).isEqualTo("该问题已在下个版本修复");
        assertThat(vo.getHandledByName()).isEqualTo("kingdo404");
        assertThat(vo.getHandledAt()).isNotNull();
    }

    @Test
    @DisplayName("countPending：单表 COUNT（type + status=pending 两个条件）")
    void countPending_countsByTypeAndStatus() {
        when(moderationTaskMapper.selectCount(any())).thenReturn(4L);

        assertThat(service().countPending(ModerationConst.TYPE_CORRECTION)).isEqualTo(4L);
        verify(moderationTaskMapper).selectCount(any());
    }

    @Test
    @DisplayName("findByTypeAndId：任务行缺失 → null；存在 → 回填状态（供处置前置判据）")
    void findByTypeAndId_returnsNullOrProjection() {
        when(moderationTaskMapper.selectOne(any())).thenReturn(null);
        assertThat(service().findByTypeAndId(ModerationConst.TYPE_CORRECTION, 5L)).isNull();

        when(moderationTaskMapper.selectOne(any())).thenReturn(task(ModerationConst.STATUS_PENDING));
        ModerationTaskVO vo = service().findByTypeAndId(ModerationConst.TYPE_FEEDBACK, 5L);
        assertThat(vo).isNotNull();
        assertThat(vo.getStatus()).isEqualTo(ModerationConst.STATUS_PENDING);
    }
}
