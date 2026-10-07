package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A6 菜品种类取值出参（管理端；`/admin/dish-categories` 的别名面）。
 *
 * <p>= **系统维度（菜品种类）** 下的取值行：`id` 即 `dish.meal_type_id` 的存储值，
 * `dishCount` = 引用该种类的菜品数（删除保护与列表展示用）。
 * 同一批行也可经 A4 的取值端点读写，本 VO 只是「种类视角」下的字段收敛。
 */
@Data
@Schema(description = "管理端菜品种类取值出参")
public class DishCategoryAdminVO {

    @Schema(description = "取值ID（= dish.meal_type_id 的存储值）")
    private Long id;

    @Schema(description = "种类中文名（可改，改名免费）", example = "面食粉类")
    private String label;

    @Schema(description = "组内展示顺序")
    private Integer order;

    @Schema(description = "引用该种类的菜品数（删除前判断）")
    private Long dishCount;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
