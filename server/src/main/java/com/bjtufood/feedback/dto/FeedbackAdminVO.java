package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

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

    @Schema(description = "反馈类型：suggestion/add/error/bug/report/other")
    private String type;

    @Schema(description = "反馈内容")
    private String content;

    @Schema(description = "联系方式")
    private String contact;

    @Schema(description = "关联类型：举报为 review；信息纠错为 dish；其他为 null")
    private String relatedType;

    @Schema(description = "关联对象ID（举报：评价ID；信息纠错：菜品ID）；其他为 null")
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
