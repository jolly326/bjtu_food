package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜品大类字典项（{@code GET /dishes/meal-types} 出参，2026-09-21 §7.34 / 方案 B）。
 * <p>
 * 首项固定下发「为你推荐」（{@code value = null, order = 0}），对应不传 {@code mealType}；
 * 后续项为当前有在售菜品的大类（空类自动隐藏，有菜自动出现）。端上标签栏全量直出渲染，零硬编码。
 * 管理端录入菜品时排除 {@code value == null} 的运营首项。
 *
 * @see com.bjtufood.dish.constant.MealTypeConst
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜品大类字典项")
public class MealTypeVO {

    @Schema(description = "大类枚举值（首项推荐为 null，其余用于 GET /dishes?mealType= 筛选）", example = "noodle")
    private String value;

    @Schema(description = "中文标签（端上直接渲染）", example = "面食粉类")
    private String label;

    @Schema(description = "标签栏展示顺序（升序）", example = "3")
    private Integer order;
}
