package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * A6 分类值合并请求。
 *
 * <p>清理同义值（「面食」/「面食类」）的**唯一手段**：服务端把 `dish.meal_type = from.key` 的菜品
 * 全部改写为 `to.key`，随后删除 `from` 行（同事务）。
 */
@Data
@Schema(description = "分类值合并请求")
public class DishCategoryMergeReq {

    @Schema(description = "被合并的分类值 ID（其下菜品改指目标后本行被删除）", example = "7")
    @NotNull(message = "fromId 不能为空")
    private Long fromId;

    @Schema(description = "目标分类值 ID（必须存在，且 ≠ fromId）", example = "3")
    @NotNull(message = "toId 不能为空")
    private Long toId;
}
