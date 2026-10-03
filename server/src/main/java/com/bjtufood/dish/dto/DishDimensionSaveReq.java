package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A4 维度新增 / 修改请求。
 *
 * <p>`fieldKey` 是数据锚点（= `dish.attributes` 的 JSON 键），**在用后不可改**；
 * 顺序走 `PUT /admin/dish-dimensions/sort`（拖拽整体提交），故本请求**不含** `order`。
 */
@Data
@Schema(description = "属性维度保存请求")
public class DishDimensionSaveReq {

    @Schema(description = "维度键（camelCase，字母开头；在用后不可改）", example = "dietType")
    @NotBlank(message = "维度键不能为空")
    @Size(max = 32, message = "维度键不能超过 32 字符")
    @Pattern(regexp = "^[a-z][a-zA-Z0-9]*$", message = "维度键须为 camelCase（小写字母开头，仅字母与数字）")
    private String fieldKey;

    @Schema(description = "维度中文名（1~32 字）", example = "饮食属性")
    @NotBlank(message = "维度名不能为空")
    @Size(max = 32, message = "维度名不能超过 32 字")
    private String name;

    @Schema(description = "取值类型：single=单值 / multi=多值（切换会自动迁移该维度下菜品的数据形状）", example = "single")
    @NotBlank(message = "取值类型不能为空")
    private String valueType;
}
