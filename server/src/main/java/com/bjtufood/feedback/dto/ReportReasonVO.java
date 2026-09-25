package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 举报原因字典项（GET /feedback/report-reasons 单行出参）。
 * <p>
 * 值域与文案的唯一真源 = {@code FeedbackConst.REPORT_REASONS}；端上举报弹层的单选项
 * 与管理端原因翻译**共用本字典，零硬编码**（PR-12）。
 */
@Data
@Schema(description = "举报原因字典项")
public class ReportReasonVO {

    @Schema(description = "机器值（提交举报时作为 sub 上送）", example = "spam")
    private String value;

    @Schema(description = "中文标签（端上直接渲染）", example = "垃圾广告 / 营销刷屏")
    private String label;

    @Schema(description = "展示顺序（从 1 起，后端已按序下发）", example = "1")
    private Integer order;

    public ReportReasonVO(String value, String label, Integer order) {
        this.value = value;
        this.label = label;
        this.order = order;
    }
}
