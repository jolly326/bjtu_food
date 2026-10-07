package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A4 维度出参（管理端）。
 *
 * <p>契约真源：docs/api/web/dimensions.md ——
 * `dishCount`（使用该维度的菜品数）与 `valueCount`（该维度下的取值数）供**删除前判断**与确认文案使用；
 * `system` 标记系统维度（菜品种类）——其取值类型不可改、维度不可删。
 */
@Data
@Schema(description = "管理端属性维度出参")
public class DishDimensionAdminVO {

    @Schema(description = "维度ID（= 菜品 attributes JSON 的键）")
    private Long id;

    @Schema(description = "维度中文名（可改，改名免费）", example = "饮食属性")
    private String name;

    @Schema(description = "取值类型：single / multi")
    private String valueType;

    @Schema(description = "是否系统维度（true = 菜品种类：取值类型不可改、维度不可删）")
    private Boolean system;

    @Schema(description = "展示顺序（升序）")
    private Integer order;

    @Schema(description = "使用该维度的菜品数（删除前判断）")
    private Long dishCount;

    @Schema(description = "该维度下的取值数（删除确认文案用）")
    private Long valueCount;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
