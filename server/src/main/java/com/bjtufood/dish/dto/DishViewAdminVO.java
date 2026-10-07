package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A6 视图出参（管理端）。
 *
 * <p>`matchedCount` 为当前匹配的在售菜品数（列表直接展示，兼作「是否生效」的自证）；
 * `conditions` / `sortKind` 回显当前口径 —— 视图的全部口径都在库里，后台可读可改。
 */
@Data
@Schema(description = "管理端筛选视图出参")
public class DishViewAdminVO {

    @Schema(description = "视图ID（端上回传 view=<id>）")
    private Long id;

    @Schema(description = "tab 文案", example = "面食粉类")
    private String label;

    @Schema(description = "展示顺序（升序）")
    private Integer order;

    @Schema(description = "是否在 client 首页出现")
    private Boolean enabled;

    @Schema(description = "筛选条件（AND；[] = 不筛选）")
    private List<DishViewCondition> conditions;

    @Schema(description = "排序口径（白名单：random / priceAsc / priceDesc / discountDesc / ratingDesc / newest）",
            example = "random")
    private String sortKind;

    @Schema(description = "当前匹配的在售菜品数")
    private Long matchedCount;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
