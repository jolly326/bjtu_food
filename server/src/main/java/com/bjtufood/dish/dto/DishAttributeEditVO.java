package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 属性编辑态项（{@code GET /dishes/{id}/attributes} 单行出参）。
 * <p>
 * <b>编辑态专用、按需</b>：只返回该菜<b>现有维度</b>，且只补编辑要用的（{@code valueType} +
 * 该维度全部候选 {@code options}）——维度名与当前值在 {@code GET /dishes/{id}} 里已有，本端点不重复下发。
 * <b>{@code options} 为空数组 = 自由文本维度</b>（无候选值）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜品属性编辑态项（按菜现有维度）")
public class DishAttributeEditVO {

    @Schema(description = "维度键（camelCase；与 GET /dishes/{id} 的 attributes[].fieldKey 对齐）", example = "flavorTags")
    private String fieldKey;

    @Schema(description = "取值类型：single=单值 / multi=多值（值取数组）", example = "multi")
    private String valueType;

    /** 该维度全部候选值（按 order 升序）；空数组 = 自由文本维度 */
    @Schema(description = "该维度全部候选值（按 order 升序；空数组 = 自由文本维度）")
    private List<DishAttributeOptionItem> options;
}
