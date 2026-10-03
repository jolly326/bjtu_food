package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * A6 视图新建 / 修改请求。
 *
 * <p>`key` 全站唯一、**在用后不可改**（历史端上缓存的 `view=` 会全部对不上）；
 * **新增的视图恒为「非默认」**，要设默认请调 `PUT /admin/dish-views/{id}/default`。
 * `order` 不从保存请求传（新建默认排最后；顺序走 `/sort` 批量端点）。
 */
@Data
@Schema(description = "筛选视图保存请求")
public class DishViewSaveReq {

    @Schema(description = "视图键（小写字母 / 数字 / -，1~32；全站唯一；在用后不可改）", example = "noodle")
    @NotBlank(message = "视图键不能为空")
    @Size(max = 32, message = "视图键不能超过 32 字符")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "视图键只能包含小写字母、数字与 -")
    private String key;

    @Schema(description = "tab 文案（1~32 字）", example = "面食粉类")
    @NotBlank(message = "tab 文案不能为空")
    @Size(max = 32, message = "tab 文案不能超过 32 字")
    private String label;

    @Schema(description = "筛选条件（AND；空数组 = 全部菜品）；字段与操作符须在白名单内")
    @NotNull(message = "筛选条件不能为 null（无筛选请传空数组）")
    private List<DishViewCondition> conditions;

    @Schema(description = "排序口径（白名单：heat/priceAsc/priceDesc/discountDesc/ratingDesc/newest/random）", example = "heat")
    @NotBlank(message = "排序口径不能为空")
    private String sortKind;

    @Schema(description = "是否在 client 首页出现（默认视图不可停用）")
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
