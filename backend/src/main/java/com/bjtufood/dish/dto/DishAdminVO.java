package com.bjtufood.dish.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "后台菜品列表展示信息")
public class DishAdminVO {

    @Schema(description = "菜品ID")
    private Long id;

    @Schema(description = "所属档口ID")
    private Long stallId;

    @Schema(description = "菜品名称", example = "牛肉拉面")
    private String name;

    @Schema(description = "价格（分）", example = "1200")
    private Integer price;

    @Schema(description = "原价（分，折扣前）", example = "1500")
    private Integer originalPrice;

    @Schema(description = "促销价（分，可空；非空视为有折扣）", example = "1200")
    private Integer promoPrice;

    @Schema(description = "菜品描述")
    private String description;

    @JsonIgnore
    @Schema(hidden = true)
    private String imagesJson;

    @Schema(description = "菜品多图URL列表")
    private List<String> images;

    @Schema(description = "标签", example = "recommended")
    private String tags;

    @Schema(description = "状态（on/off）", example = "on")
    private String status;

    @Schema(description = "浏览量")
    private Integer viewCount;

    @Schema(description = "收藏量（喜欢总数）")
    private Integer favoriteCount;

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

    @Schema(description = "辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣", example = "0")
    private Integer spiceLevel;

    @Schema(description = "分量枚举：0=小 1=中 2=大", example = "1")
    private Integer portion;

    @Schema(description = "供应时段 tag，逗号分隔：breakfast/lunch/dinner/midnight", example = "lunch,dinner")
    private String servePeriod;

    @Schema(description = "是否限量：0=否 1=是", example = "0")
    private Integer limited;
}
