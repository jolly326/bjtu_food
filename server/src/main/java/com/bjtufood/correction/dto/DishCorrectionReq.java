package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交菜品信息纠错请求（{@code POST /dishes/{id}/correction}，dishId 在路径上）。
 * <p>
 * 七字段快照 = 用户修正后的新值（食堂名 / 档口名为自由文本，无字典端点）：
 * name 必填 ≤64 字且敏感词命中即 400；price 必填整数 &gt;0（分）；
 * canteenName / stallName 必填 ≤64 字；flavorTags / ingredients / images 可选
 * （images ≤9 张，COS 白名单校验）。
 */
@Data
@Schema(description = "提交菜品信息纠错请求")
public class DishCorrectionReq {

    @Schema(description = "菜品名称（必填，≤64 字）", example = "宫保鸡丁", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "菜品名称不能为空")
    @Size(max = 64, message = "菜品名称不能超过64字")
    private String name;

    @Schema(description = "现价（必填，单位分，>0）", example = "1600", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "价格不能为空")
    @Positive(message = "价格必须为大于 0 的整数（单位：分）")
    private Integer price;

    @Schema(description = "食堂名称（必填，≤64 字）", example = "学一食堂", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "食堂名称不能为空")
    @Size(max = 64, message = "食堂名称不能超过64字")
    private String canteenName;

    @Schema(description = "档口名称（必填，≤64 字）", example = "学一基本伙食", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "档口名称不能为空")
    @Size(max = 64, message = "档口名称不能超过64字")
    private String stallName;

    @Schema(description = "口味标签（机器值数组，可选）", example = "[\"spicy\",\"sour\"]")
    private List<String> flavorTags;

    @Schema(description = "主料/食材（机器值数组，可选）", example = "[\"chicken\",\"veg\"]")
    private List<String> ingredients;

    @Schema(description = "菜品图片 URL 列表（经 POST /upload/images 转存的 COS 绝对地址，≤9 张，可选）")
    private List<String> images;
}
