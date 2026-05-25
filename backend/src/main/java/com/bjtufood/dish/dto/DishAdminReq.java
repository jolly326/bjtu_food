package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 菜品管理请求 DTO
 * <p>
 * 食堂管理员新增/编辑菜品时使用
 */
@Data
@Schema(description = "菜品管理请求参数")
public class DishAdminReq {

    @NotBlank(message = "菜品名称不能为空")
    @Schema(description = "菜品名称", example = "红烧牛肉面", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "价格不能为空")
    @Schema(description = "价格（分）", example = "1500", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer price;

    @Schema(description = "菜品描述", example = "手工拉面配红烧牛肉")
    private String description;

    @Schema(description = "菜品图片URL")
    private String image;

    @Schema(description = "标签", example = "recommended")
    private String tags;

    @Schema(description = "状态（on=上架, off=下架）", example = "on")
    private String status;
}
