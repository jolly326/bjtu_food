package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A4 维度出参（管理端）。
 *
 * <p>契约真源：docs/api/web/dimensions.md ——
 * `dishCount`（使用该维度的菜品数）与 `valueCount`（该维度下的取值数）供**删除前判断**与确认文案使用。
 */
@Data
@Schema(description = "管理端属性维度出参")
public class DishDimensionAdminVO {

    @Schema(description = "维度ID")
    private Long id;

    @Schema(description = "维度键（= 菜品 attributes 的键，camelCase；**在用后不可改**）", example = "dietType")
    private String fieldKey;

    @Schema(description = "维度中文名（可改，改名免费）", example = "饮食属性")
    private String name;

    @Schema(description = "取值类型：single / multi")
    private String valueType;

    @Schema(description = "展示顺序（升序）")
    private Integer order;

    @Schema(description = "使用该维度的菜品数（删除前判断）")
    private Long dishCount;

    @Schema(description = "该维度下的取值数（删除确认文案用）")
    private Long valueCount;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
