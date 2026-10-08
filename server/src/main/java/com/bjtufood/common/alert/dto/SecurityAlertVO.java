package com.bjtufood.common.alert.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 安全告警记录出参（管理端「安全告警」面板）。
 *
 * <p>标签类字段（{@code alertTypeLabel} / {@code severityLabel}）直接取库中快照，
 * 面板不再反查枚举 —— 枚举重命名不会让历史记录失去可读性。
 */
@Data
@Schema(description = "安全告警记录")
public class SecurityAlertVO {

    @Schema(description = "告警记录 ID")
    private Long id;

    @Schema(description = "告警类型键（可据此筛选）")
    private String alertType;

    @Schema(description = "告警类型中文标签")
    private String alertTypeLabel;

    @Schema(description = "级别键：info 提示 / warn 警告 / critical 严重")
    private String severity;

    @Schema(description = "级别中文标签")
    private String severityLabel;

    @Schema(description = "告警标题")
    private String title;

    @Schema(description = "告警明细（脱敏）")
    private String detail;

    @Schema(description = "来源 IP（无 Web 上下文时为空串）")
    private String sourceIp;

    @Schema(description = "发生时间（yyyy-MM-dd HH:mm:ss）")
    private String createdAt;
}
