package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A6 菜品种类重命名请求（**只改 `name`**，改名免费 —— 数据锚在种类 `id`）。
 */
@Data
@Schema(description = "菜品种类重命名请求")
public class DishCategoryRenameReq {

    @Schema(description = "种类中文名（1~32 字；全表唯一）", example = "面食粉类")
    @NotBlank(message = "分类名不能为空")
    @Size(max = 32, message = "分类名不能超过 32 字")
    private String name;
}
