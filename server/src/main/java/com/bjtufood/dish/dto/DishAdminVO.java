package com.bjtufood.dish.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台菜品列表展示信息（管理端专用）。
 * <p>
 * 与公开 VO 的差异：保留 {@code status}（上下架，管理端需处置）、{@code stallId} /
 * {@code createdAt} / {@code updatedAt}（管理端维护用）；<b>不返回 {@code viewCount}</b>
 * （§7.29 / dish-field-contract：浏览量仅服务热度排序，管理端不展示）。
 * 原 promoPrice / tags / spiceLevel / region 已下线，新增四维。
 */
@Data
@Schema(description = "后台菜品列表展示信息")
public class DishAdminVO {

    @Schema(description = "菜品ID")
    private Long id;

    @Schema(description = "所属档口ID")
    private Long stallId;

    @Schema(description = "菜品名称", example = "牛肉拉面")
    private String name;

    @Schema(description = "搜索别名（逗号分隔，管理员配置，可空）", example = "拉面,牛肉面")
    private String alias;

    @Schema(description = "现价（分，已含折扣）", example = "1200")
    private Integer price;

    @Schema(description = "原价（分，可空）", example = "1500")
    private Integer originalPrice;

    @Schema(description = "菜品描述")
    private String description;

    @JsonIgnore
    @Schema(hidden = true)
    private String imagesJson;

    @Schema(description = "菜品多图URL列表")
    private List<String> images;

    @Schema(description = "状态（on/off）", example = "on")
    private String status;

    @Schema(description = "平均评分", example = "4.5")
    private BigDecimal avgRating;

    @Schema(description = "评价数")
    private Integer ratingCount;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "档口名称", example = "面食窗口")
    private String stallName;

    @Schema(description = "所属食堂名称", example = "第一食堂")
    private String canteenName;

    // ==================== 描述四维（§7.28） ====================

    @Schema(description = "荤素/饮食属性：meat=荤 / half=半荤 / veg=素 / halal=清真", example = "half")
    private String dietType;

    @Schema(description = "主料/食材（逗号分隔）：pork/beef/lamb/chicken/duck/fish/egg/tofu/mushroom/veg/noodle/rice", example = "chicken,rice")
    private String ingredients;

    @Schema(description = "口味（逗号分隔）：spicy/numbing/sour/sweet/salty/umami/light/heavy", example = "spicy,sour")
    private String flavorTags;

    @Schema(description = "冷热：hot=热食 / room=常温 / ice=冰", example = "hot")
    private String serveTemp;
}
