package com.bjtufood.dish.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜品视图对象（VO）
 * <p>
 * 展示给前端的菜品信息，包含关联的档口/食堂名称等冗余字段
 * 用于 GET /api/dishes（列表）和 GET /api/dishes/{id}（详情）
 */
@Data
@Schema(description = "菜品展示信息")
public class DishVO {

    @Schema(description = "菜品ID")
    private Long id;

    @Schema(description = "菜品名称", example = "牛肉拉面")
    private String name;

    /** 价格（分），前端自行转换显示为元 */
    @Schema(description = "价格（分）", example = "1200")
    private Integer price;

    @Schema(description = "菜品描述")
    private String description;

    /** 数据库原始 images JSON 字符串，由 Service 层解析为 List */
    @JsonIgnore
    @Schema(hidden = true)
    private String imagesJson;

    @Schema(description = "菜品多图URL列表")
    private List<String> images;

    @Schema(description = "标签", example = "recommended")
    private String tags;

    @Schema(description = "所属档口ID")
    private Long stallId;

    @Schema(description = "档口名称", example = "面食窗口")
    private String stallName;

    @Schema(description = "所属食堂ID")
    private Long canteenId;

    @Schema(description = "食堂名称", example = "第一食堂")
    private String canteenName;

    @Schema(description = "平均评分", example = "4.5")
    private BigDecimal avgRating;

    @Schema(description = "评价数", example = "20")
    private Integer ratingCount;

    @Schema(description = "收藏量", example = "15")
    private Integer collectCount;

    @Schema(description = "浏览量", example = "200")
    private Integer viewCount;

    @Schema(description = "状态（on/off）", example = "on")
    private String status;

    // ==================== 以下字段仅详情页接口返回 ====================

    @Schema(description = "当前用户是否已收藏（仅登录用户）")
    private Boolean isFavorited;

    @Schema(description = "当前用户是否已评价（仅登录用户）")
    private Boolean hasReviewed;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
