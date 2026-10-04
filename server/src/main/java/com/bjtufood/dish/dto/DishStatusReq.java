package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 上下架请求（{@code PUT /admin/dishes/{id}/status}）。
 * <p>
 * 契约见 docs/api/web/dishes.md：只改 {@code status}，`on` 上架 / `off` 下架；
 * 取值非法 → `400`，目标菜品不存在 → `4001`。
 */
@Data
@Schema(description = "菜品上下架请求")
public class DishStatusReq {

    @Schema(description = "状态：on 上架 / off 下架", example = "off")
    @NotBlank(message = "status 不能为空")
    private String status;
}
