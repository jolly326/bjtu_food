package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜品描述四维字典项（{@code GET /dishes/attributes} 出参，2026-09-23 §7.40 R4）。
 * <p>
 * 小程序端与管理端**共用同一份字典**：端上用它把菜品出参的机器值映射为中文展示值，
 * 管理端另用它渲染表单下拉 / chips 选项 —— 两端 SHALL NOT 再硬编码「机器值 → 中文」映射表。
 *
 * @see com.bjtufood.dish.constant.DishAttributeConst
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜品描述四维字典项")
public class DishAttributeVO {

    @Schema(description = "维度字段名（与菜品出参字段名逐字一致，消费方直接据此匹配，R13）",
            example = "dietType")
    private String field;

    @Schema(description = "机器值（业务数据下发的值，如菜品出参的 dietType / ingredients 元素）",
            example = "meat")
    private String value;

    @Schema(description = "中文标签（消费端直接渲染）", example = "荤")
    private String label;

    @Schema(description = "组内展示顺序（升序）", example = "1")
    private Integer order;
}
