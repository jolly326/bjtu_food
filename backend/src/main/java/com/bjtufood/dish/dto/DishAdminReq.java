package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "后台菜品新增/编辑请求参数")
public class DishAdminReq {

    @NotNull(message = "档口ID不能为空")
    @Schema(description = "所属档口ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long stallId;

    @NotBlank(message = "菜品名称不能为空")
    @Schema(description = "菜品名称", example = "番茄炒蛋盖饭", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "价格不能为空")
    @Schema(description = "价格，单位：分。1200 表示 12 元。", example = "1200", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer price;

    @Schema(description = "菜品描述", example = "学生餐厅常见基础套餐")
    private String description;

    @Schema(description = "菜品图片 URL 列表。单图时只放一个 URL。", example = "[\"/images/seed/dishes/tomato-egg.jpg\"]")
    private List<String> images;

    @Schema(description = "标签，多个标签用英文逗号分隔", example = "daily,recommended")
    private String tags;

    @Schema(description = "状态：on=上架，off=下架", example = "on")
    private String status;
}
