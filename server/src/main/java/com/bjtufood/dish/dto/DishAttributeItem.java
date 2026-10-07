package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜品描述属性展示项（{@code GET /dishes/{id}} 的 {@code attributes[]} 单行）。
 * <p>
 * <b>值即中文</b>：{@code value} 直接是中文文本（存储层 {@code dish.attributes} 存取值 ID），
 * 后端翻译后下发，端上直接渲染、<b>不拉字典、不做映射</b>。
 * <p>
 * <b>{@code value} 形态</b>：{@code single} 维度为字符串、{@code multi} 维度为字符串数组。
 * <b>自描述</b>：只含该菜品实际拥有的维度，按维度展示顺序排列；某维度无值则不出现、不占位。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜品描述属性展示项（值即中文）")
public class DishAttributeItem {

    @Schema(description = "维度 ID（= dish.attributes JSON 的键）", example = "3")
    private Long dimensionId;

    @Schema(description = "维度中文名", example = "口味")
    private String name;

    /** 中文值：single → 字符串；multi → 字符串数组 */
    @Schema(description = "中文值（single=字符串 / multi=字符串数组）", example = "[\"辣\",\"酸\"]")
    private Object value;
}
