package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 举报原因字典项（{@code GET /report-reasons} 单行出参）。
 * <p>
 * 值域与文案的唯一真源 = **`report_reason` 表**；端上举报弹层的单选项
 * 与管理端原因翻译**共用本字典，零硬编码**。
 * 出参恰 {@code id} / {@code label} 两项：顺序由服务端下发次序表达，端上按序渲染、不读序号字段。
 */
@Data
@Schema(description = "举报原因字典项")
public class ReportReasonVO {

    @Schema(description = "原因 ID（提交举报时作为 reasonId 上送）", example = "1")
    private Long id;

    @Schema(description = "中文标签（端上直接渲染）", example = "垃圾广告 / 营销刷屏")
    private String label;

    public ReportReasonVO(Long id, String label) {
        this.id = id;
        this.label = label;
    }
}
