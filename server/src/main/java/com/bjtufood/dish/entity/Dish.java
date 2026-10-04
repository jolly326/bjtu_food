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
 * 价格以"分"为单位存储（如 12.00 元 = 1200 分），避免浮点精度问题。
 * 价格口径：唯一数据源为 {@code price}（现价，已含折扣），
 * {@code originalPrice} 为可空原价；原「促销价」promoPrice 与列已全链删除。
 * 描述属性：由 {@code attributes} JSON 列承载（动态属性模型，两表字典驱动）。
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

    /** 现价（单位：分，已含折扣） */
    @Schema(description = "现价（分，已含折扣）", example = "1200")
    private Integer price;

    /** 原价（可空，单位：分）；originalPrice > price 视为有折扣 */
    @Schema(description = "原价（分，可空）", example = "1500")
    private Integer originalPrice;

    /** 菜品描述 */
    @Schema(description = "菜品描述")
    private String description;

    /** 菜品多图，JSON 字符串 */
    @Schema(description = "菜品多图JSON")
    private String images;

    /**
     * 描述属性（动态属性模型，JSON 对象）：键 = 维度 {@code fieldKey}（camelCase），
     * 值 = 机器值（{@code single} 维度为字符串 / {@code multi} 维度为字符串数组）；
     * 仅含该菜品实际拥有的维度。字典真源 = {@code dish_attribute_dimension} + {@code dish_attribute_value}。
     * 列 ↔ 展示项的整理（机器值 + 中文标签）由 Service 层完成，本字段只持存储形态。
     */
    @Schema(description = "描述属性（JSON：键=维度 fieldKey，值=机器值/数组）", example = "{\"dietType\":\"veg\",\"flavorTags\":[\"spicy\"]}")
    private String attributes;

    /** 菜品大类：值域由视图字典表派生（单一真源）；
     *  每个菜品恰属一个大类（互斥、全量覆盖目标）；不进公开菜品出参（DishListItemVO / DishDetailVO），
     *  仅供「大类视图」筛选与视图字典下发 */
    @Schema(description = "菜品分类键（值域 = dish_category_value 分类值字典；自由输入自动登记）", example = "noodle")
    private String mealType;

    /** 状态：on（上架）/ off（下架）（菜品审核语义已整体退役，见 docs/schema/dish.md） */
    @Schema(description = "状态", example = "on")
    private String status;

    // dish.reject_reason / dish.created_by 已于用户拍板「零消费即删除」退役：
    // reject_reason 恒 NULL（审核语义退役后无写入入口）、created_by 只写不读（upsert 留痕撤销）；
    // CREATE TABLE 已移除，存量库已直连远程库清理。

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
