package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A6 视图修改请求（后台只维护 tab 的**文案 / 启停**）。
 *
 * <p>`key` / 筛选条件 / 排序口径属 seed / 代码资产，**不可通过后台修改**；
 * 顺序走独立的 `PUT /admin/dish-views/sort` 批量端点。
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
}
