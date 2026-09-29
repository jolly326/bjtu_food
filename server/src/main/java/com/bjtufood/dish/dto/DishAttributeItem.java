package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜品描述属性展示项（{@code GET /dishes/{id}} 的 {@code attributes[]} 单行）。
 * <p>
 * <b>R4（后端整理、端上零翻译）</b>：机器值 {@code value} 与其<b>中文标签</b> {@code label} 由后端一并下发，
 * 端上直接渲染、<b>不拉字典、不做「机器值 → 中文」映射</b>——中文只在后端一处维护。
 * <p>
 * <b>{@code label} 与 {@code value} 同构</b>：{@code single} 维度为字符串、{@code multi} 维度为字符串数组。
 * <b>自描述</b>：只含该菜品实际拥有的维度，按维度展示顺序排列；某维度无值则不出现、不占位。
 * <p>
 * <b>字典未命中</b>（旧数据 / 脏值）→ 后端原样透出机器值（不丢弃、不报错）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜品描述属性展示项")
public class DishAttributeItem {

    @Schema(description = "维度键（camelCase）", example = "flavorTags")
    private String fieldKey;

    @Schema(description = "维度中文名", example = "口味")
    private String name;

    /** 机器值：single → 字符串；multi → 字符串数组（与 label 同构） */
    @Schema(description = "机器值（single=字符串 / multi=字符串数组）", example = "[\"spicy\",\"sour\"]")
    private Object value;

    /** 中文标签：与 value 同构（single=字符串 / multi=字符串数组） */
    @Schema(description = "中文标签（与 value 同构）", example = "[\"辣\",\"酸\"]")
    private Object label;
}
