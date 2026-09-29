package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 属性编辑态候选项（{@code GET /dishes/{id}/attributes} 的 {@code options[]} 单行）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "属性编辑态候选项")
public class DishAttributeOptionItem {

    @Schema(description = "机器值", example = "spicy")
    private String valueKey;

    @Schema(description = "中文标签", example = "辣")
    private String label;
}
