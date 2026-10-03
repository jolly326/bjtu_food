package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A6 视图出参（管理端）。
 *
 * <p>`conditions` **原样回显**供编辑；`matchedCount` 为当前匹配的在售菜品数（列表直接展示，
 * 兼作「是否生效」的自证）。
 */
@Data
@Schema(description = "管理端筛选视图出参")
public class DishViewAdminVO {

    @Schema(description = "视图ID")
    private Long id;

    @Schema(description = "视图键（端上回传 view=<key>；在用后不可改）", example = "noodle")
    private String key;

    @Schema(description = "tab 文案", example = "面食粉类")
    private String label;

    @Schema(description = "展示顺序（升序；默认视图排首位）")
    private Integer order;

    @Schema(description = "是否在 client 首页出现")
    private Boolean enabled;

    @Schema(description = "是否默认视图（端上不带 view 的落点；全站恰一个）")
    private Boolean isDefault;

    @Schema(description = "筛选条件（原样回显，供编辑；空数组 = 全部菜品）")
    private List<DishViewCondition> conditions;

    @Schema(description = "排序口径", example = "heat")
    private String sortKind;

    @Schema(description = "当前匹配的在售菜品数")
    private Long matchedCount;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
