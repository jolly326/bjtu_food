package com.bjtufood.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志视图对象（管理端只读）
 */
@Data
@Schema(description = "操作日志展示信息")
public class OperationLogVO {

    @Schema(description = "日志ID")
    private Long id;

    @Schema(description = "操作管理员ID")
    private Long adminId;

    @Schema(description = "操作管理员昵称")
    private String adminNickname;

    @Schema(description = "动作标识")
    private String action;

    @Schema(description = "操作对象类型")
    private String targetType;

    @Schema(description = "操作对象ID")
    private Long targetId;

    @Schema(description = "操作来源IP")
    private String ip;

    @Schema(description = "操作时间")
    private LocalDateTime createdAt;
}
