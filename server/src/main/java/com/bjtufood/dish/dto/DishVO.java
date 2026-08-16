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

    /** 原价（分，折扣前）；promoPrice 非空视为有折扣 */
    @Schema(description = "原价（分，折扣前）", example = "1500")
    private Integer originalPrice;

    /** 促销价（分，可空）；非空视为有折扣 */
    @Schema(description = "促销价（分，可空；非空视为有折扣）", example = "1200")
    private Integer promoPrice;

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

    /** 档口楼层（如 1F/2F），来自 stall 联表 */
    @Schema(description = "档口楼层（如 1F/2F）", example = "1F")
    private String floor;

    /** 档口窗口号，来自 stall 联表 */
    @Schema(description = "档口窗口号", example = "3号窗口")
    private String windowNo;

    /** 食堂坐标（GCJ-02），来自 canteen 联表；前端本地 Haversine 算「距你 Xm」用，服务器不算距离 */
    @Schema(description = "食堂纬度（GCJ-02），前端本地算距离用", example = "39.90")
    private BigDecimal latitude;

    /** 食堂经度（GCJ-02），来自 canteen 联表 */
    @Schema(description = "食堂经度（GCJ-02），前端本地算距离用", example = "116.40")
    private BigDecimal longitude;

    /** 档口营业时间，来自 stall 联表 */
    @Schema(description = "档口营业时间，如 10:00-20:00", example = "10:00-20:00")
    private String businessHours;

    @Schema(description = "平均评分", example = "4.5")
    private BigDecimal avgRating;

    @Schema(description = "评价数", example = "20")
    private Integer ratingCount;

    @Schema(description = "浏览量", example = "200")
    private Integer viewCount;

    @Schema(description = "是否为新品")
    private Boolean isNew;

    @Schema(description = "状态（on/off）", example = "on")
    private String status;

    // ==================== 以下字段仅详情页接口返回 ====================

    @Schema(description = "当前用户是否已评价（仅登录用户）")
    private Boolean hasReviewed;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    // ==================== 一期新增菜品属性标签字段 ====================

    /** 辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣 */
    @Schema(description = "辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣", example = "0")
    private Integer spiceLevel;

    /** 地域（美食来源地）：如 清真/川湘/西北/粤式/东北 等，与食堂位置无关 */
    @Schema(description = "地域（美食来源地），如 清真/川湘/西北/粤式/东北", example = "清真")
    private String region;

    /** 分量枚举：0=小 1=中 2=大 */
    @Schema(description = "分量枚举：0=小 1=中 2=大", example = "1")
    private Integer portion;

    /** 供应时段 tag，逗号分隔：breakfast/lunch/dinner/midnight */
    @Schema(description = "供应时段 tag，逗号分隔：breakfast/lunch/dinner/midnight", example = "lunch,dinner")
    private String servePeriod;

    /** 是否限量 */
    @Schema(description = "是否限量", example = "false")
    private Boolean limited;

    /**
     * 距用户距离（米），仅当请求携带 lat/lng 时由后端计算返回；
     * 无坐标食堂或请求未带坐标时为 null（前端「距你 Xm」缺省不展示）。
     */
    @Schema(description = "距用户距离（米），请求携带坐标时返回；否则为 null", example = "320")
    private Integer distance;
}
