package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 菜品详情视图对象（VO）——**详情专用**，15 个字段 + {@code ratingDistribution}
 * <p>
 * 2026-09-22 用户拍板「列表 / 详情出参拆分」（docs/feature/client-首页菜品浏览.md D 项）：
 * 本类不再作为列表出参（原共享 {@code DishVO} 及其 {@code extends} 形态均已废止、该类已删除），只服务 {@code GET /dishes/{id}}；
 * 列表改由 {@link DishListItemVO}（8 字段）承载，本类的详情专属字段不得回流到列表。
 * <p>
 * 字段集（15）：{@code id}、{@code name}、{@code price}、{@code originalPrice}、{@code description}、
 * {@code images}、{@code stallName}、{@code canteenName}、{@code floor}、{@code avgRating}、
 * {@code ratingCount}、{@code dietType}、{@code ingredients}、{@code flavorTags}、{@code serveTemp}。
 * <p>
 * 以下字段已从公开响应删除且不得回流：{@code status}（公开接口恒只返回在售）、{@code createdAt}、
 * {@code canteenId}、{@code stallId}、{@code viewCount}、{@code promoPrice}、{@code tags}、
 * {@code spiceLevel}、{@code region}、{@code windowNo}、{@code updatedAt}、{@code latitude}、
 * {@code longitude}、{@code mealType}（大类只参与筛选与字典下发，不进公开出参）。
 * <p>
 * 价格口径（D2）：{@code price} = 现价（唯一数据源）；{@code originalPrice} = 原价（可空）；
 * 「有折扣」判据为 {@code originalPrice} 有值且大于 {@code price}。
 */
@Data
@Schema(description = "菜品详情展示信息（公开 15 字段 + 评分分布）")
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

    @Schema(description = "菜品多图URL列表（列 ↔ List 转换由 StringListTypeHandler 在持久层完成；2026-09-23 R5）")
    private List<String> images;

    @Schema(description = "档口名称", example = "面食窗口")
    private String stallName;

    @Schema(description = "食堂名称", example = "第一食堂")
    private String canteenName;

    /** 档口楼层（如 1F/2F），来自 stall 联表 */
    @Schema(description = "档口楼层（如 1F/2F）", example = "1F")
    private String floor;

    @Schema(description = "平均评分", example = "4.5")
    private BigDecimal avgRating;

    @Schema(description = "评价数", example = "20")
    private Integer ratingCount;

    // ==================== 描述四维（§7.28） ====================

    /** 荤素/饮食属性：meat=荤 / half=半荤 / veg=素 / halal=清真 */
    @Schema(description = "荤素/饮食属性：meat=荤 / half=半荤 / veg=素 / halal=清真", example = "half")
    private String dietType;

    /** 主料/食材：机器值数组（**2026-09-23 R4 由逗号分隔串改数组**；中文标签见 `GET /dishes/attributes`） */
    @Schema(description = "主料/食材（数组）：pork/beef/lamb/chicken/duck/fish/egg/tofu/mushroom/veg/noodle/rice", example = "[\"chicken\",\"veg\"]")
    private List<String> ingredients;

    /** 口味：机器值数组（吸收原辣度语义；**2026-09-23 R4 改数组**） */
    @Schema(description = "口味（数组）：spicy/numbing/sour/sweet/salty/umami/light/heavy", example = "[\"spicy\",\"sour\"]")
    private List<String> flavorTags;

    /** 冷热：hot=热食 / room=常温 / ice=冰 */
    @Schema(description = "冷热：hot=热食 / room=常温 / ice=冰", example = "hot")
    private String serveTemp;

    // ==================== 详情专属附加 ====================

    @Schema(description = "评分分布（1-5星各一个）")
    private List<RatingDistributionVO> ratingDistribution;
}
