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
     * 楼层（**归属档口**，非菜品）：楼层是 {@code stall.floor} 的属性，菜品无楼层字段。
     * <p>
     * 采纳时写回「目标档口」的 {@code stall.floor}——同档口下的其他菜品<b>一并生效</b>
     * （楼层是档口级描述，不是单菜属性）。自由文本（无字典端点），传入即校验非空、≤16 字
     * （与 {@code stall.floor VARCHAR(16)} 对齐）。
     */
    @Schema(description = "楼层（自由文本，归属档口 stall.floor；传入时：非空、≤16 字）", example = "1F")
    private String floor;

    /**
     * 动态描述属性（局部：仅含用户改动的维度项）。键 = 维度 {@code fieldKey}，值 = **中文文本**（或文本数组）；
     * 取值为自由文本、候选仅作提示不限制；命中内容安检则 400。
     */
    @Schema(description = "动态描述属性（键=维度 fieldKey，值=中文文本/数组；仅传改动维度）",
            example = "{\"dietType\":\"素\",\"flavorTags\":[\"辣\",\"酸\"]}")
    private Map<String, Object> attributes;

    @Schema(description = "菜品图片 URL 列表（经 POST /upload/cloud-image 转存的 COS 绝对地址；**≤5 张** —— "
            + "纠错需多张佐证，上限宽于评价 / 反馈的 3 张）")
    private List<String> images;
}
