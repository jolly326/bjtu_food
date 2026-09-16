package com.bjtufood.dish.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 菜品实体类
 * <p>
 * 对应数据库表：dish
 * 价格以"分"为单位存储（如 12.00 元 = 1200 分），避免浮点精度问题
 */
@Data
@TableName("dish")
@Schema(description = "菜品")
public class Dish {

    @TableId(type = IdType.AUTO)
    @Schema(description = "菜品ID")
    private Long id;

    /** 所属档口ID */
    @Schema(description = "所属档口ID")
    private Long stallId;

    /** 菜品名称 */
    @Schema(description = "菜品名称", example = "牛肉拉面")
    private String name;

    /** 搜索别名（逗号分隔，管理员配置，可空）；搜索关键词命中别名也能找到该菜品 */
    @Schema(description = "搜索别名（逗号分隔，管理员配置，可空）", example = "拉面,牛肉面")
    private String alias;

    /** 价格（单位：分） */
    @Schema(description = "价格（分）", example = "1200")
    private Integer price;

    /** 原价（折扣前，单位：分）；promoPrice 非空视为有折扣 */
    @Schema(description = "原价（分，折扣前）", example = "1500")
    private Integer originalPrice;

    /** 促销价（单位：分，可空）；非空视为有折扣 */
    @Schema(description = "促销价（分，可空；非空视为有折扣）", example = "1200")
    private Integer promoPrice;

    /** 菜品描述 */
    @Schema(description = "菜品描述")
    private String description;

    /** 菜品多图，JSON 字符串 */
    @Schema(description = "菜品多图JSON")
    private String images;

    /** 标签，逗号分隔（recommended=必吃, signature=招牌） */
    @Schema(description = "标签", example = "recommended,signature")
    private String tags;

    /** 辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣 */
    @Schema(description = "辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣", example = "0")
    private Integer spiceLevel;

    /** 风味/菜系：如 清真/川湘/西北/粤式/东北 等，与食堂位置无关 */
    @Schema(description = "风味/菜系，如 清真/川湘/西北/粤式/东北", example = "清真")
    private String region;

    /** 状态：on（上架）/ off（下架）（菜品审核语义已整体退役，见 schema.sql dish 表注释） */
    @Schema(description = "状态", example = "on")
    private String status;

    // dish.reject_reason / dish.created_by 已于 2026-09-16 用户拍板「零消费即删除」退役：
    // reject_reason 恒 NULL（审核语义退役后无写入入口）、created_by 只写不读（upsert 留痕撤销）；
    // CREATE TABLE 已移除，存量库由 schema.sql 末尾 drop_zero_consumer_columns 幂等段清理。

    /** 浏览量 */
    @Schema(description = "浏览量")
    private Integer viewCount;

    /** 平均评分 */
    @Schema(description = "平均评分", example = "4.5")
    private BigDecimal avgRating;

    /** 评价数 */
    @Schema(description = "评价数")
    private Integer ratingCount;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
