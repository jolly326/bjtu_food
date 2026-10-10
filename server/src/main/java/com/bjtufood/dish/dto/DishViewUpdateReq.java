package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A6 视图修改请求（**文案 / 启停 / 归属种类整体替换**）。
 *
 * <p>三个字段一次提交，缺任一 → `400`（不出现「漏传字段导致旧值残留」）。
 */
@Data
@Schema(description = "筛选视图修改请求")
public class DishViewUpdateReq {

    @Schema(description = "tab 文案（1~32 字）", example = "面食粉类")
    @NotBlank(message = "tab 文案不能为空")
    @Size(max = 32, message = "tab 文案不能超过 32 字")
    private String name;

    @Schema(description = "是否在 client 首页出现（停用 = tab 隐藏）")
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;

    @Schema(description = "归属种类 ID（值域 = dish_category.id；null = 不按种类过滤）", example = "3")
    private Long categoryId;
}
