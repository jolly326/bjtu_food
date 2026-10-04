package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A6 分类值出参（管理端）。
 *
 * <p><b>`key` 必须出参</b>：视图条件里的 `mealType` 值就是分类键（`dish.meal_type` 的存储值），
 * 条件构建器要据此组装 `{ field: 'mealType', op: '=', value: <key> }` —— 少了它管理端无法构造条件。
 */
@Data
@Schema(description = "管理端分类值出参")
public class DishCategoryAdminVO {

    @Schema(description = "分类ID")
    private Long id;

    @Schema(description = "分类键（dish.meal_type 的存储值；视图条件引用它；在用后不可改）", example = "noodle")
    private String key;

    @Schema(description = "分类中文名（可改，改名免费）", example = "面食粉类")
    private String label;

    @Schema(description = "顺序（后台下拉 / 列表展示序）")
    private Integer order;

    @Schema(description = "引用该分类的菜品数（删除前判断）")
    private Long dishCount;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
