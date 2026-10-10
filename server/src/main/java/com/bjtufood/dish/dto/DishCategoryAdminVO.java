package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A6 菜品种类出参（管理端；`/admin/dish-categories`）。
 *
 * <p>对应字典表 `dish_category` 的一行：`id` 即 `dish.category_id` 的存储值，
 * `dishCount` = 引用该种类的菜品数（被引用时不可删，故用于删除保护与列表展示）。
 */
@Data
@Schema(description = "管理端菜品种类出参")
public class DishCategoryAdminVO {

    @Schema(description = "种类ID（= dish.category_id 的存储值，值域 = dish_category.id）")
    private Long id;

    @Schema(description = "种类中文名（可改，改名免费）", example = "面食粉类")
    private String label;

    @Schema(description = "展示顺序")
    private Integer order;

    @Schema(description = "引用该种类的菜品数（删除前判断）")
    private Long dishCount;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
