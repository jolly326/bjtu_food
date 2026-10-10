package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A6 视图出参（管理端）。
 *
 * <p>`matchedCount` 为当前匹配的在售菜品数（列表直接展示，兼作「是否生效」的自证）；
 * `categoryId` 回显归属种类（{@code null} = 不按种类过滤）—— 视图的全部口径都在库里，后台可读可改。
 */
@Data
@Schema(description = "管理端筛选视图出参")
public class DishViewAdminVO {

    @Schema(description = "视图ID（端上回传 view=<id>）")
    private Long id;

    @Schema(description = "tab 文案", example = "面食粉类")
    private String name;

    @Schema(description = "归属种类ID（null = 不按种类过滤）", example = "3")
    private Long categoryId;

    @Schema(description = "是否在 client 首页出现")
    private Boolean enabled;

    @Schema(description = "当前匹配的在售菜品数")
    private Long matchedCount;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
