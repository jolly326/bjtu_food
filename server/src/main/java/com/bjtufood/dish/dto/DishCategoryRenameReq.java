package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A6 菜品种类取值重命名请求（**只改 `label`**，改名免费 —— 数据锚在取值 `id`）。
 */
@Data
@Schema(description = "菜品种类取值重命名请求")
public class DishCategoryRenameReq {

    @Schema(description = "分类中文名（1~32 字；分类名唯一）", example = "面食粉类")
    @NotBlank(message = "分类名不能为空")
    @Size(max = 32, message = "分类名不能超过 32 字")
    private String label;
}
