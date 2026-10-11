package com.bjtufood.moderation.constant;

import java.util.Set;

/**
 * 管理端处置任务（`moderation_task`）值域与文案约束单一真源。
 * <p>
 * 三类用户提交（意见反馈 / 评价举报 / 菜品问题反馈）**只存提交事实**，其「处置状态 + 处置文案」
 * 一律外置到处置任务表：提交时在<b>同一事务</b>内预建一行 {@code status=pending}，
 * 管理端处置时更新该行并投递站内回执快照。故本类的值域即「三类工单共用的处置状态机」。
 * <p>
 * 口径真源：docs/schema/moderation_task.md 与 docs/api/web/feedback.md、reports.md、corrections.md。
 */
public interface ModerationConst {

    // ==================== type（工单类型 = 多态键，无外键） ====================

    /** 意见反馈（`feedback` 表） */
    String TYPE_FEEDBACK = "feedback";
    /** 评价举报（`report` 表） */
    String TYPE_REPORT = "report";
    /** 菜品问题反馈（`correction` 表） */
    String TYPE_CORRECTION = "correction";

    /** 工单类型白名单（唯一索引 `(type, type_id)` 的前半段） */
    Set<String> TYPES = Set.of(TYPE_FEEDBACK, TYPE_REPORT, TYPE_CORRECTION);

    // ==================== status（处置状态） ====================

    /** 待处置（提交事务内预建即为本值） */
    String STATUS_PENDING = "pending";
    /** 已处置 */
    String STATUS_HANDLED = "handled";

    /** 处置状态白名单（管理端列表筛选入参校验用；不传 = 全部） */
    Set<String> STATUSES = Set.of(STATUS_PENDING, STATUS_HANDLED);

    // ==================== outcome（处置结论） ====================

    /** 处置结论：通过 / 已处理（缺省） */
    String OUTCOME_HANDLED = "handled";
    /** 处置结论：不采纳 / 退回（此时处置文案必填） */
    String OUTCOME_REJECTED = "rejected";

    /** 处置结论白名单（处置入参校验用；未传按 handled 缺省） */
    Set<String> OUTCOMES = Set.of(OUTCOME_HANDLED, OUTCOME_REJECTED);

    // ==================== 处置文案（快照列，与 notification 同名两列） ====================

    /** 处置回执短标题最大长度（字） */
    int REPLY_TITLE_MAX_LENGTH = 40;

    /**
     * 处置回执正文最大长度（字）。
     * <p>
     * 与 `notification.reply_content` 的列宽一致口径：回执正文由本列快照下发，
     * 故本上限**不得**放宽而不改列宽。
     */
    int REPLY_CONTENT_MAX_LENGTH = 600;
}
