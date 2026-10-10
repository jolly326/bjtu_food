package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A6 视图新建请求（`POST /admin/dish-views`）。
 *
 * <p>不含 `enabled`：新建即为启用状态。{@code categoryId} 缺省 = 不按种类过滤（聚合视角）；
 * 取数口径固定为「种类过滤（可空）+ 随机序 + 在售」。
 */
@Data
@Schema(description = "筛选视图新建请求")
public class DishViewCreateReq {

    @Schema(description = "tab 文案（1~32 字）", example = "面食粉类")
    @NotBlank(message = "tab 文案不能为空")
    @Size(max = 32, message = "tab 文案不能超过 32 字")
    private String name;

    @Schema(description = "归属种类 ID（值域 = dish_category.id；缺省 = 不按种类过滤）", example = "3")
    private Long categoryId;
}
