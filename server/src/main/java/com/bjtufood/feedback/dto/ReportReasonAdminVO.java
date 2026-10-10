package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A7 举报原因出参（管理端）。
 *
 * <p>比公开 VO（{@link ReportReasonVO}）多 3 项：`status` / `feedbackCount` / `updatedAt`
 * —— 支撑列表展示与「删除前判断」。契约真源：docs/api/web/report-reasons.md。
 */
@Data
@Schema(description = "管理端举报原因出参")
public class ReportReasonAdminVO {

    @Schema(description = "原因ID（落库列 = user_feedback.sub_reason_id）")
    private Long id;

    @Schema(description = "中文名", example = "垃圾广告 / 营销刷屏")
    private String name;

    @Schema(description = "状态：on=启用 / off=停用")
    private String status;

    @Schema(description = "被举报记录引用次数（type='report' 且 sub_reason_id=id）—— 删除前判断")
    private Long feedbackCount;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
