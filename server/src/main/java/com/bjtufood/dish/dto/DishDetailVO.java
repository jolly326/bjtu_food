package com.bjtufood.dish.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 菜品详情视图对象（VO）——**详情专用**，恰 11 个字段
 * <p>
 * 只服务 {@code GET /dishes/{id}}；列表由 {@link DishListItemVO}（8 字段）承载，
 * 本类的详情专属字段不得回流到列表。
 * <p>
 * 字段集（11）：{@code id}、{@code name}、{@code price}、{@code originalPrice}、{@code description}、
 * {@code images}、{@code stallName}、{@code canteenName}、{@code floor}、{@code avgRating}、{@code attributes}。
 * <p>
 * 以下字段已从公开响应删除且不得回流：{@code status}（公开接口恒只返回在售）、{@code createdAt}、
 * {@code canteenId}、{@code stallId}、{@code viewCount}、{@code promoPrice}、{@code tags}、
 * {@code spiceLevel}、{@code region}、{@code windowNo}、{@code updatedAt}、{@code latitude}、
 * {@code longitude}、{@code mealType}（大类只参与筛选与字典下发，不进公开出参）、
 * {@code ratingCount}（零消费）、{@code ratingDistribution}（零消费）、
 * {@code dietType} / {@code ingredients} / {@code flavorTags} / {@code serveTemp}
 * （四维已收敛为 {@code attributes} 动态属性）。
 * <p>
 * 价格口径：{@code price} = 现价（唯一数据源）；{@code originalPrice} = 原价（可空）；
 * 「有折扣」判据为 {@code originalPrice} 有值且大于 {@code price}。
 */
@Data
@Schema(description = "菜品详情展示信息（公开 11 字段）")
public class DishDetailVO {

    @Schema(description = "菜品ID")
    private Long id;

    @Schema(description = "菜品名称", example = "牛肉拉面")
    private String name;

    /** 现价（分），唯一价格数据源；前端自行转换显示为元 */
    @Schema(description = "现价（分，已含折扣）", example = "1200")
    private Integer price;

    /** 原价（分，可空）；originalPrice > price 视为有折扣 */
    @Schema(description = "原价（分，可空）", example = "1500")
    private Integer originalPrice;

    @Schema(description = "菜品描述")
    private String description;

    @Schema(description = "菜品多图URL列表（列 ↔ List 转换由 StringListTypeHandler 在持久层完成）")
    private List<String> images;

    @Schema(description = "档口名称", example = "面食窗口")
    private String stallName;

    @Schema(description = "食堂名称", example = "第一食堂")
    private String canteenName;

    /** 档口楼层（如 1F/2F），来自 stall 联表 */
    @Schema(description = "档口楼层（如 1F/2F）", example = "1F")
    private String floor;

    /**
     * 平均评分：<b>读缓存列 {@code dish.avg_rating}</b>（口径 = 仅未隐藏评价，由评价写操作异步重算）；
     * 该菜品零评价时为 {@code null}——端上以本字段是否 null 判「有无评分」。
     */
    @Schema(description = "平均评分（读缓存列 dish.avg_rating；零评价为 null）", example = "4.5")
    private BigDecimal avgRating;

    /** 该菜品实际拥有的描述属性（值即中文），按维度展示顺序排列 */
    @Schema(description = "菜品描述属性（仅含该菜实际拥有的维度，按维度顺序；value：single=字符串 / multi=数组）")
    private List<DishAttributeItem> attributes;

    /**
     * {@code dish.attributes} 列的 JSON 原文，**仅供 Service 解析 {@link #attributes}，不出参**
     * （存储形态不泄漏进契约；展示项由 {@link #attributes} 承载）。
     */
    @JsonIgnore
    @Schema(hidden = true)
    private String attributesJson;
}
