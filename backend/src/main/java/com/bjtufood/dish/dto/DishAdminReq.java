package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "后台菜品新增/编辑请求参数")
public class DishAdminReq {

    /**
     * 所属档口ID。
     * 注意：DTO 层不做 @NotNull 校验——PUT /admin/dishes/{id} 支持「部分更新」，
     * 前端行内状态 Switch（仅传 status）依赖此能力；新增（POST）时的必填校验在 Service 层完成。
     */
    @Schema(description = "所属档口ID", example = "1")
    private Long stallId;

    @Schema(description = "菜品名称", example = "番茄炒蛋盖饭")
    private String name;

    @Schema(description = "价格，单位：分。1200 表示 12 元。", example = "1200")
    private Integer price;

    @Schema(description = "原价（分，折扣前）。1500 表示 15 元。", example = "1500")
    private Integer originalPrice;

    @Schema(description = "促销价（分，可空）；非空视为有折扣。1200 表示 12 元。", example = "1200")
    private Integer promoPrice;

    @Schema(description = "菜品描述", example = "学生餐厅常见基础套餐")
    private String description;

    @Schema(description = "菜品图片 URL 列表。单图时只放一个 URL。", example = "[\"/images/seed/dishes/tomato-egg.jpg\"]")
    private List<String> images;

    @Schema(description = "标签，多个标签用英文逗号分隔", example = "daily,recommended")
    private String tags;

    @Schema(description = "辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣", example = "0")
    private Integer spiceLevel;

    @Schema(description = "分量枚举：0=小 1=中 2=大", example = "1")
    private Integer portion;

    @Schema(description = "供应时段 tag，逗号分隔：breakfast/lunch/dinner/midnight", example = "lunch,dinner")
    private String servePeriod;

    @Schema(description = "是否限量：0=否 1=是", example = "0")
    private Integer limited;

    @Schema(description = "状态：on=上架，off=下架", example = "on")
    private String status;
}
