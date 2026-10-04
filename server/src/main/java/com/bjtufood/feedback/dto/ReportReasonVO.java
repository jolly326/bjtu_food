package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 举报原因字典项（{@code GET /report-reasons} 单行出参）。
 * <p>
 * 值域与文案的唯一真源 = **`report_reason` 表**（A7 落地后由常量改为表驱动，
 * {@code FeedbackConst.REPORT_REASONS} 已删除）；端上举报弹层的单选项
 * 与管理端原因翻译**共用本字典，零硬编码**（PR-12）。
 * 出参恰 {@code value} / {@code label} 两项：顺序由服务端下发次序表达，端上按序渲染、不再读序号字段。
 */
@Data
@Schema(description = "举报原因字典项")
public class ReportReasonVO {

    @Schema(description = "机器值（提交举报时作为 sub 上送）", example = "spam")
    private String value;

    @Schema(description = "中文标签（端上直接渲染）", example = "垃圾广告 / 营销刷屏")
    private String label;

    public ReportReasonVO(String value, String label) {
        this.value = value;
        this.label = label;
    }
}
