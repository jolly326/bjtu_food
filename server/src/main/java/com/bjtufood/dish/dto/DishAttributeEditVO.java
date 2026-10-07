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
 * 参考候选 {@code options}）——维度名与当前值在 {@code GET /dishes/{id}} 里已有，本端点不重复下发。
 * <p>
 * <b>{@code options}</b> 为该维度参考候选（取值字典中文，按字典序），<b>仅为参考、不构成约束</b>：
 * 端上恒允许自由输入候选之外的值；候选为空 = 暂无参考值（仍可自由输入）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜品属性编辑态项（按菜现有维度）")
public class DishAttributeEditVO {

    @Schema(description = "维度 ID（与 GET /dishes/{id} 的 attributes[].dimensionId 对齐；提交时即 attributes 的键）", example = "3")
    private Long dimensionId;

    @Schema(description = "取值类型：single=单值 / multi=多值（值取数组）", example = "multi")
    private String valueType;

    /** 该维度参考候选值（中文文本，按字典序）；空数组 = 暂无参考值（仍可自由输入） */
    @Schema(description = "该维度参考候选值（中文文本；空数组=暂无参考值）")
    private List<String> options;
}
