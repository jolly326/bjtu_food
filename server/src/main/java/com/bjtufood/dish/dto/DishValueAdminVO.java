package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A4 取值出参（管理端）。
 *
 * <p>`dishCount` = 引用该取值的菜品数（删除前判断；取值被引用时禁止删除）。
 */
@Data
@Schema(description = "管理端属性取值出参")
public class DishValueAdminVO {

    @Schema(description = "取值ID（菜品 attributes 引用的就是它）")
    private Long id;

    @Schema(description = "所属维度ID")
    private Long dimensionId;

    @Schema(description = "取值中文名（可改，改名免费）", example = "半荤")
    private String label;

    @Schema(description = "组内展示顺序（升序）")
    private Integer order;

    @Schema(description = "引用该取值的菜品数（删除前判断）")
    private Long dishCount;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
