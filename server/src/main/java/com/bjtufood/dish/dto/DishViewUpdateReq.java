package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * A6 视图修改请求（**文案 / 启停 / 筛选条件 / 排序口径整体替换**）。
 *
 * <p>四个字段一次提交，缺任一 → `400`（不出现「漏传字段导致旧值残留」）；
 * 顺序走独立的 `PUT /admin/dish-views/sort` 批量端点。
 * 条件与排序口径的白名单见 {@code DishViewConditions}。
 */
@Data
@Schema(description = "筛选视图修改请求")
public class DishViewUpdateReq {

    @Schema(description = "tab 文案（1~32 字）", example = "面食粉类")
    @NotBlank(message = "tab 文案不能为空")
    @Size(max = 32, message = "tab 文案不能超过 32 字")
    private String label;

    @Schema(description = "是否在 client 首页出现（停用 = tab 隐藏）")
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;

    @Schema(description = "筛选条件（AND；[] = 不筛选）")
    @NotNull(message = "conditions 不能为空（不筛选请传 []）")
    private List<DishViewCondition> conditions;

    @Schema(description = "排序口径（白名单：random / priceAsc / priceDesc / discountDesc / ratingDesc / newest）",
            example = "random")
    @NotBlank(message = "排序口径不能为空")
    private String sortKind;
}
