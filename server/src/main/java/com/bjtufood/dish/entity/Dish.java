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
 * 价格口径（2026-09-20 拍板 §7.26）：唯一数据源为 {@code price}（现价，已含折扣），
 * {@code originalPrice} 为可空原价；原「促销价」promoPrice 与列已全链删除。
 * 描述维度（2026-09-20 拍板 §7.28）：四维 dietType/ingredients/flavorTags/serveTemp 替换原 spiceLevel/region。
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

    /** 荤素/饮食属性（§7.28）：meat=荤 / half=半荤 / veg=素 / halal=清真（单选，可空） */
    @Schema(description = "荤素/饮食属性：meat=荤 / half=半荤 / veg=素 / halal=清真", example = "half")
    private String dietType;

    /** 主料/食材：逗号分隔机器值（§7.28，可空） */
    @Schema(description = "主料/食材（逗号分隔）：pork/beef/lamb/chicken/duck/fish/egg/tofu/mushroom/veg/noodle/rice", example = "chicken,rice")
    private String ingredients;

    /** 口味：逗号分隔机器值（§7.28，吸收原辣度语义，可空） */
    @Schema(description = "口味（逗号分隔）：spicy/numbing/sour/sweet/salty/umami/light/heavy", example = "spicy,sour")
    private String flavorTags;

    /** 冷热（§7.28）：hot=热食 / room=常温 / ice=冰（单选，可空） */
    @Schema(description = "冷热：hot=热食 / room=常温 / ice=冰", example = "hot")
    private String serveTemp;

    /**
     * 菜品大类（2026-09-21 §7.34，单值枚举）：set_meal 套餐盖饭 / stir_fry 家常小炒 /
     * noodle 面食粉类 / dry_pot 香锅干锅 / snack 风味小吃 / soup_drink 汤饮甜品（可空）。
     * 口径：一个菜品恰属一个大类；与属性维度（dietType/ingredients/flavorTags/serveTemp）分开；
     * 判定按「菜名与做法形态」。取值白名单见 {@code MealTypeConst}；**不进公开 DishVO 出参**。
     */
    @Schema(description = "菜品大类（单值）：set_meal 套餐盖饭 / stir_fry 家常小炒 / noodle 面食粉类 / dry_pot 香锅干锅 / snack 风味小吃 / soup_drink 汤饮甜品", example = "noodle")
    private String mealType;

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
