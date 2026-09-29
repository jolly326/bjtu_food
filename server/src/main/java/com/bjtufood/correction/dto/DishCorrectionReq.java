package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 提交菜品信息纠错请求（{@code POST /dishes/{id}/correction}，dishId 在路径上）。
 * <p>
 * <b>局部提交（patch）</b>：请求体<b>只含用户改动过的字段</b>——未传 = 保持原值不变，未改动的项不必上传；
 * 故下表字段<b>均为选填</b>，「校验（传入时）」一列表示该字段<b>一旦传入</b>必须满足的约束。
 * <b>空请求体（无任何改动项）→ 400「未提交任何改动」</b>。
 * <p>
 * 请求体无 {@code type}、无 {@code dishId}（{@code dishId} 在路径中）；描述属性经 {@code attributes} 动态提交。
 */
@Data
@Schema(description = "提交菜品信息纠错请求（局部提交：仅传改动项）")
public class DishCorrectionReq {

    @Schema(description = "菜品名称（传入时：非空、≤64 字）", example = "宫保鸡丁")
    private String name;

    @Schema(description = "现价（传入时：>0 的整数，单位分）", example = "1600")
    private Integer price;

    @Schema(description = "食堂名称（自由文本；传入时：非空、≤64 字）", example = "学一食堂")
    private String canteenName;

    @Schema(description = "档口名称（自由文本；传入时：非空、≤64 字）", example = "学一基本伙食")
    private String stallName;

    /**
     * 动态描述属性（局部：仅含用户改动的维度项）。键 = 维度 {@code fieldKey}，值 = 机器值或数组；
     * 有候选值的维度取值受限取值表，候选为空的维度可自由文本。
     */
    @Schema(description = "动态描述属性（键=维度 fieldKey，值=机器值/数组；仅传改动维度）",
            example = "{\"dietType\":\"veg\",\"flavorTags\":[\"spicy\",\"sour\"]}")
    private Map<String, Object> attributes;

    @Schema(description = "菜品图片 URL 列表（经 POST /upload/cloud-image 转存的 COS 绝对地址，传入时：≤3 张）")
    private List<String> images;
}
