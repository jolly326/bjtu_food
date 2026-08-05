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

    /** 分量枚举：0=小 1=中 2=大 */
    @Schema(description = "分量枚举：0=小 1=中 2=大", example = "1")
    private Integer portion;

    /** 供应时段 tag，逗号分隔：breakfast/lunch/dinner/midnight */
    @Schema(description = "供应时段 tag，逗号分隔：breakfast/lunch/dinner/midnight", example = "lunch,dinner")
    private String servePeriod;

    /** 是否限量（0=否 1=是） */
    @Schema(description = "是否限量（0=否 1=是）", example = "0")
    private Integer limited;

    /** 状态：on（上架）/ off（下架），与审核状态解耦 */
    @Schema(description = "状态", example = "on")
    private String status;

    /**
     * 审核状态（与上下架 status 解耦）：pending（待审核）/ approved（已通过）/ rejected（已退回）
     * 学生 UGC 提交后写入 pending；后台通过置 approved、退回置 rejected 并回写 reject_reason。
     */
    @Schema(description = "审核状态：pending/approved/rejected", example = "pending")
    private String auditStatus;

    /** 退回原因（仅 audit_status=rejected 时由后台填写，可空） */
    @Schema(description = "退回原因（audit_status=rejected 时由后台填写）")
    private String rejectReason;

    /** 提交人用户ID（学生 UGC 由当前登录用户写入，禁止前端传入） */
    @Schema(description = "提交人用户ID")
    private Long createdBy;

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
