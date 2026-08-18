package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 反馈管理端视图对象
 */
@Data
@Schema(description = "反馈管理端展示信息")
public class FeedbackAdminVO {

    @Schema(description = "反馈ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户昵称")
    private String userNickname;

    @Schema(description = "反馈类型：suggestion/error/other/report")
    private String type;

    @Schema(description = "反馈内容")
    private String content;

    @Schema(description = "附图（绝对URL数组，2026-08-17 新增）")
    private List<String> images;

    @Schema(description = "联系方式")
    private String contact;

    @Schema(description = "关联类型（举报场景）：moment；其他为 null")
    private String relatedType;

    @Schema(description = "关联对象ID（举报场景：动态ID）；其他为 null")
    private Long relatedId;

    @Schema(description = "处理状态：pending/handled")
    private String status;

    @Schema(description = "管理员回复")
    private String reply;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "处理时间")
    private LocalDateTime handledAt;
}
