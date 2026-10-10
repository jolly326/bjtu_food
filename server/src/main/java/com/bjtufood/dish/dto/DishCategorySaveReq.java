package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A6 菜品种类登记请求（`POST /admin/dish-categories`）。
 *
 * <p>管理员只填中文名：种类 ID 由后端生成（不入参）。
 */
@Data
@Schema(description = "菜品种类登记请求")
public class DishCategorySaveReq {

    @Schema(description = "种类中文名（1~32 字；全表唯一）", example = "面食粉类")
    @NotBlank(message = "分类名不能为空")
    @Size(max = 32, message = "分类名不能超过 32 字")
    private String name;
}
