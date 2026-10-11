package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 意见反馈管理端视图对象（{@code GET /admin/feedbacks}）。
 * <p>
 * 「处置状态 + 处置文案」全部来自处置任务表（{@code moderation_task}，按 {@code type_id} 批量回填）——
 * 反馈表本身只提供提交事实（提交人 / 类型 / 正文 / 配图 / 提交时间）。
 */
@Data
@Schema(description = "反馈管理端展示信息")
public class FeedbackAdminVO {

    @Schema(description = "反馈ID")
    private Long id;

    @Schema(description = "提交人用户ID（恒为真实用户 ID —— 列带外键 fk_feedback_user → user(id)）")
    private Long userId;

    @Schema(description = "提交人昵称（账号已查无时为「已注销用户」以外的空值由前端兜底）")
    private String userName;

    @Schema(description = "反馈类型ID（feedback_option 字典项 ID）")
    private Long optionId;

    @Schema(description = "反馈类型中文名（由 feedback_option 字典实时翻译，停用项仍可译出）")
    private String optionName;

    @Schema(description = "反馈正文（空串兜底）")
    private String content;

    @Schema(description = "反馈配图 URL 列表（COS 绝对地址，≤3 张；恒为数组）")
    private List<String> images;

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
