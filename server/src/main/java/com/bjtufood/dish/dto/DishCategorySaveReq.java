package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A6 分类值登记请求（`POST /admin/dish-categories`）。
 *
 * <p>这是「**自动登记**」的最终落点：A3 菜品保存 / A6 视图条件保存时输入的新分类，
 * 最终都会走到本端点（或同事务等价逻辑）。
 */
@Data
@Schema(description = "分类值登记请求")
public class DishCategorySaveReq {

    @Schema(description = "分类键（小写字母 / 数字 / -，1~20；全站唯一；在用后不可改）", example = "noodle")
    @NotBlank(message = "分类键不能为空")
    @Size(max = 20, message = "分类键不能超过 20 字符")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "分类键只能包含小写字母、数字与 -")
    private String key;

    @Schema(description = "分类中文名（1~32 字；分类名唯一）", example = "面食粉类")
    @NotBlank(message = "分类名不能为空")
    @Size(max = 32, message = "分类名不能超过 32 字")
    private String label;
}
