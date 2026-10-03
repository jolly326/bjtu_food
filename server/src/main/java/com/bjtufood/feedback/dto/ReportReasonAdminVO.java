package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A7 举报原因出参（管理端）。
 *
 * <p>比公开 VO（{@link ReportReasonVO}）多 4 项：`id` / `order` / `status` / `feedbackCount` / `updatedAt`
 * —— 支撑列表展示与「删除前判断」。契约真源：docs/web/A-主数据维护/A7-举报原因管理.md。
 */
@Data
@Schema(description = "管理端举报原因出参")
public class ReportReasonAdminVO {

    @Schema(description = "原因ID")
    private Long id;

    @Schema(description = "机器值（端上提交字段名 = reason；落库列 = user_feedback.sub）", example = "spam")
    private String value;

    @Schema(description = "中文标签", example = "垃圾广告 / 营销刷屏")
    private String label;

    @Schema(description = "展示顺序（升序）")
    private Integer order;

    @Schema(description = "状态：on=启用 / off=停用")
    private String status;

    @Schema(description = "被举报记录引用次数（type='report' 且 sub=value）—— 删除前判断")
    private Long feedbackCount;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
