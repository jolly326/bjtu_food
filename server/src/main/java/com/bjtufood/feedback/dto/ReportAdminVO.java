package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报管理端视图对象（{@code GET /admin/reports}）。
 * <p>
 * 「处置状态 + 处置文案」来自处置任务表（{@code moderation_task}，按 {@code type_id} 批量回填）；
 * 被举报评价摘要经 review 域只读契约**批量**补齐（逐行取会退化成 N+1）。
 * <p>
 * 🔴 <b>被举报评价已删除</b>：{@code report.review_id} 在评价被物理删除时被数据库置 NULL
 * （外键 {@code ON DELETE SET NULL}）⇒ 举报行**保留**、{@link #reviewDeleted} 置 true，
 * 管理端据此显示「被举报评价已删除」，举报人 / 原因 / 处置状态仍然完整。
 */
@Data
@Schema(description = "举报管理端展示信息")
public class ReportAdminVO {

    @Schema(description = "举报ID")
    private Long id;

    @Schema(description = "举报人用户ID（恒为真实用户 ID —— 列带外键 fk_report_user → user(id)）")
    private Long userId;

    @Schema(description = "举报人昵称")
    private String userName;

    @Schema(description = "举报原因ID（report_option 字典项 ID）")
    private Long optionId;

    @Schema(description = "举报原因中文名（由 report_option 字典实时翻译，停用项仍可译出）")
    private String optionName;

    @Schema(description = "被举报评价ID（被举报评价已删除为 null）")
    private Long reviewId;

    @Schema(description = "被举报评价是否已删除（reviewId 为 null 即 true；此时摘要与可见性均为 null）")
    private Boolean reviewDeleted;

    @Schema(description = "被举报评价正文摘要（评价已删除为 null；完整内容去评价管理看）")
    private String reviewContent;

    @Schema(description = "被举报评价所属菜品名（评价已删除为 null）")
    private String reviewDishName;

    @Schema(description = "被举报评价当前是否已隐藏（决定「同时隐藏」复选是否置灰；评价已删除为 null）")
    private Boolean reviewHidden;

    @Schema(description = "处置状态：pending / handled")
    private String status;

    @Schema(description = "处置结论：handled=通过 / rejected=不采纳；未处置为 null")
    private String outcome;

    @Schema(description = "处置回执短标题（未处置为 null）")
    private String replyTitle;

    @Schema(description = "处置文案 / 不采纳原因（未处置为 null）")
    private String replyContent;

    @Schema(description = "处置人登录名（未处置 / 账号已删除为 null）")
    private String handledByName;

    @Schema(description = "提交时间")
    private LocalDateTime createdAt;

    @Schema(description = "处置时间（未处置为 null）")
    private LocalDateTime handledAt;
}
