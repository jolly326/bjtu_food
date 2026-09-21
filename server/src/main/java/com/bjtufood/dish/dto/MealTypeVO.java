package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜品大类字典项（{@code GET /dishes/meal-types} 出参，2026-09-21 §7.34）。
 * <p>
 * 端上标签栏完全由本响应渲染（第一项「全部」由端上固定渲染，对应不传 {@code mealType}）；
 * **端上不得维护任何标签中文映射或标签清单**。本端点只下发「当前有在售菜品」的大类
 * （空类自动隐藏，有菜自动出现）。
 *
 * @see com.bjtufood.dish.constant.MealTypeConst
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜品大类字典项")
public class MealTypeVO {

    @Schema(description = "大类枚举键（用于 GET /dishes?mealType= 筛选）", example = "noodle")
    private String key;

    @Schema(description = "中文标签（端上直接渲染）", example = "面食粉类")
    private String label;

    @Schema(description = "标签栏展示顺序（升序）", example = "3")
    private Integer order;
}
