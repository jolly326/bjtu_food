package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分类字典项（公开只读端点 {@code GET /feedback-options} / {@code GET /report-options} 的单行出参）。
 * <p>
 * 值域与文案的唯一真源 = 字典表（`feedback_option` / `report_option`）；端上表单与弹层的单选项
 * 与管理端翻译**共用本字典，零硬编码**。
 * <p>
 * 出参恰 {@code id} / {@code name} 两项：顺序由服务端下发次序表达（`id` 升序），端上按序渲染、不读序号字段。
 */
@Data
@Schema(description = "分类字典项")
public class OptionVO {

    @Schema(description = "选项 ID（提交时作为 optionId 上送）", example = "1")
    private Long id;

    @Schema(description = "中文名（端上直接渲染）", example = "程序功能Bug")
    private String name;

    public OptionVO(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
