package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * A6 视图新建请求（`POST /admin/dish-views`）。
 *
 * <p>不含 `order` / `enabled`：顺序由列表拖拽维护，新建即为启用状态（默认排最后）。
 * 条件与排序口径的白名单见 {@code DishViewConditions}。
 */
@Data
@Schema(description = "筛选视图新建请求")
public class DishViewCreateReq {

    @Schema(description = "tab 文案（1~32 字）", example = "面食粉类")
    @NotBlank(message = "tab 文案不能为空")
    @Size(max = 32, message = "tab 文案不能超过 32 字")
    private String label;

    @Schema(description = "筛选条件（AND；缺省 / [] 均表示不筛选）")
    private List<DishViewCondition> conditions;

    @Schema(description = "排序口径（白名单：random / priceAsc / priceDesc / discountDesc / ratingDesc / newest）",
            example = "random")
    @NotBlank(message = "排序口径不能为空")
    private String sortKind;
}
