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

    /** 所属档口ID；{@code null} = 未归入档口（「无归属」的唯一表达，不用 {@code 0} 占位） */
    @Schema(description = "所属档口ID（null = 未归入档口）")
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
     * 仅含该菜品实际拥有的描述维度（**不含菜品种类** —— 种类由独立列 {@code category_id} 承载）。
     * 字典真源 = {@code dish_attribute_dimension} + {@code dish_attribute_value}。
     * 列 ↔ 展示项的整理（中文标签翻译）由 Service 层完成，本字段只持存储形态。
     */
    @Schema(description = "描述属性（JSON：键=维度 ID，值=取值 ID/数组）", example = "{\"1\":\"2\",\"3\":[\"5\",\"7\"]}")
    private String attributes;

    /** 菜品**分类**：存 {@code dish_category.id}（单一真源）；
     *  每个菜品恰属一个分类（互斥、必填）；不进公开菜品出参（DishListItemVO / DishDetailVO），
     *  仅供视图筛选（条件 `categoryId`）与管理端录入 / 回填 */
    @Schema(description = "菜品分类 ID（值域 = dish_category.id）", example = "3")
    private Long categoryId;

    /** 状态：on（上架）/ off（下架）（菜品审核语义已取消，见 docs/schema/dish.md） */
    @Schema(description = "状态", example = "on")
    private String status;

    /**
     * 平均评分（**缓存列**，1 位小数）。
     * <p>
     * 零评价为 {@code null}（区分「无评价」与「均分 0」）；公开出参已在 Service 层兜底为
     * {@code 5.0}（仅出参层，不落库）。浏览量不在本实体上 —— 其唯一载体是
     * {@code dish_view_log} 的 30 天滚动窗口（见 {@link DishViewLog}、docs/schema/dish.md）。
     */
    @Schema(description = "平均评分（1 位小数，零评价为 null）", example = "4.5")
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
