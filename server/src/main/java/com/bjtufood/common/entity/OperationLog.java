package com.bjtufood.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体类（AOP 埋点写入，Web 只读查询）
 * <p>
 * 对应数据库表：operation_log
 */
@Data
@TableName("operation_log")
@Schema(description = "操作日志")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    @Schema(description = "日志ID")
    private Long id;

    @Schema(description = "操作管理员ID")
    private Long adminId;

    /** 动作标识：audit_approve / audit_reject / moment_hide / moment_delete / feedback_handle ... */
    @Schema(description = "动作标识")
    private String action;

    /** 操作对象类型：moment / dish / stall / canteen / feedback / review */
    @Schema(description = "操作对象类型")
    private String targetType;

    @Schema(description = "操作对象ID")
    private Long targetId;

    @Schema(description = "操作来源IP")
    private String ip;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "操作时间")
    private LocalDateTime createdAt;
}
