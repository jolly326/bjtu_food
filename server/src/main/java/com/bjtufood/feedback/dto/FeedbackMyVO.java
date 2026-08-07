package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的反馈项（反馈中心进度列表，task-09 扩展）
 */
@Data
@Schema(description = "我的反馈项")
public class FeedbackMyVO {

    @Schema(description = "反馈ID")
    private Long id;

    @Schema(description = "反馈类型：suggestion/error/other/report")
    private String type;

    @Schema(description = "反馈内容")
    private String content;

    @Schema(description = "处理状态：pending/handled")
    private String status;

    @Schema(description = "管理员回复")
    private String reply;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
