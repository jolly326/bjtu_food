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

    /** 价格（单位：分） */
    @Schema(description = "价格（分）", example = "1200")
    private Integer price;

    /** 菜品描述 */
    @Schema(description = "菜品描述")
    private String description;

    /** 菜品图片 URL */
    @Schema(description = "菜品图片URL")
    private String image;

    /** 标签，逗号分隔（recommended=必吃, signature=招牌） */
    @Schema(description = "标签", example = "recommended,signature")
    private String tags;

    /** 状态：on（上架）/ off（下架） */
    @Schema(description = "状态", example = "on")
    private String status;

    /** 浏览量 */
    @Schema(description = "浏览量")
    private Integer viewCount;

    /** 收藏量 */
    @Schema(description = "收藏量")
    private Integer collectCount;

    /** 平均评分 */
    @Schema(description = "平均评分", example = "4.5")
    private BigDecimal avgRating;

    /** 评价数 */
    @Schema(description = "评价数")
    private Integer ratingCount;

    /** 软删除标记 */
    @TableLogic
    @Schema(description = "是否删除（0=未删除, 1=已删除）")
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
