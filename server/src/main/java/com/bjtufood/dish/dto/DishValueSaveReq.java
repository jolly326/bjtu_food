package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A4 取值新增 / 改名请求（**只收 `label`** —— 数据锚在 ID，改名免费）。
 */
@Data
@Schema(description = "属性取值保存请求")
public class DishValueSaveReq {

    @Schema(description = "取值中文名（1~32 字；同维度下唯一）", example = "半荤")
    @NotBlank(message = "取值名不能为空")
    @Size(max = 32, message = "取值名不能超过 32 字")
    private String label;
}
