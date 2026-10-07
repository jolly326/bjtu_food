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
 * 价格口径：唯一数据源为 {@code price}（现价，已含折扣），{@code originalPrice} 为可空原价。
 * 描述属性：由 {@code attributes} JSON 列承载（动态属性模型，两表字典驱动），**仅描述维度**。
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
     * 描述属性（动态属性模型，JSON 对象）：键 = **描述维度** {@code id}（字符串形态的十进制 ID），
     * 值 = 取值 ID（{@code single} 维度为数字 / {@code multi} 维度为数字数组）；
     * 仅含该菜品实际拥有的描述维度（**不含系统维度「菜品种类」**）。
     * 字典真源 = {@code dish_attribute_dimension} + {@code dish_attribute_value}。
     * 列 ↔ 展示项的整理（中文标签翻译）由 Service 层完成，本字段只持存储形态。
     */
    @Schema(description = "描述属性（JSON：键=维度 ID，值=取值 ID/数组）", example = "{\"1\":\"2\",\"3\":[\"5\",\"7\"]}")
    private String attributes;

    /** 菜品**种类**：存系统维度（菜品种类）下 {@code dish_attribute_value.id}（单一真源）；
     *  每个菜品恰属一个种类（互斥、必填）；不进公开菜品出参（DishListItemVO / DishDetailVO），
     *  仅供「种类视图」筛选与种类字典下发 */
    @Schema(description = "菜品种类 ID（值域 = 系统维度「菜品种类」下的取值 id）", example = "3")
    private Long mealTypeId;

    /** 状态：on（上架）/ off（下架）（菜品审核语义已取消，见 docs/schema/dish.md） */
    @Schema(description = "状态", example = "on")
    private String status;

    /** 浏览量 */
    @Schema(description = "浏览量")
    private Integer viewCount;

    /** 平均评分 */
    @Schema(description = "平均评分", example = "4.5")
    private BigDecimal avgRating;

    /** 评价数 */
    @Schema(description = "评价数")
    private Integer ratingCount;

    /** 创建时间（DB 时钟：INSERT 由 `DEFAULT CURRENT_TIMESTAMP` 写入，应用层不写、不填充） */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /** 更新时间（DB 时钟：UPDATE 由 `ON UPDATE CURRENT_TIMESTAMP` 维护，应用层不写、不填充） */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
