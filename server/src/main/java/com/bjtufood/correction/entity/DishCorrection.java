package com.bjtufood.correction.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bjtufood.common.persistence.StringListTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜品信息纠错实体类
 * <p>
 * 对应数据库表：dish_correction（菜品信息纠错，独立于 user_feedback 反馈体系）。
 * 用户在菜品详情页提交的信息纠错快照：提交的七字段为用户修正后的新值，
 * 管理端采纳后整体写回 dish（adopt 路径），拒绝则留存不采纳原因。
 * <p>
 * 例外：{@code floor}（楼层纠错，2026-09-30 新增）归属<b>档口</b>而非菜品——菜品无楼层字段，
 * 采纳时写回目标档口的 {@code stall.floor}（同档口其他菜品一并生效），详情出参的 {@code floor} 亦来自 stall 联表。
 */
@Data
@TableName(value = "dish_correction", autoResultMap = true)
@Schema(description = "菜品信息纠错")
public class DishCorrection {

    @TableId(type = IdType.AUTO)
    @Schema(description = "纠错ID")
    private Long id;

    /** 目标菜品ID（逻辑关联 dish，无外键，与项目现状一致） */
    @Schema(description = "目标菜品ID")
    private Long dishId;

    /** 提交人用户ID（匿名提交为 NULL） */
    @Schema(description = "提交人用户ID（匿名提交为 null）")
    private Long userId;

    /** 提交的菜品名称 */
    @Schema(description = "提交的菜品名称", example = "宫保鸡丁")
    private String name;

    /** 提交的现价（单位：分） */
    @Schema(description = "提交的现价（分）", example = "1600")
    private Integer price;

    /** 提交的食堂名称（自由文本，无 GET /stalls 字典） */
    @Schema(description = "提交的食堂名称", example = "学一食堂")
    private String canteenName;

    /** 提交的档口名称（自由文本，采纳时两段式确认归档） */
    @Schema(description = "提交的档口名称", example = "学一基本伙食")
    private String stallName;

    /**
     * 提交的楼层（**改动项快照**，自由文本，未改动留 NULL）。
     * <p>
     * 楼层归属档口：采纳时写回<b>目标档口</b>的 {@code stall.floor}，而不是 dish
     * （档口归属与 {@link #stallName} 一致，均经 canteen 域服务写契约下发）。
     * 长度上限与 {@code stall.floor VARCHAR(16)} 一致，由 correction 侧提交时校验。
     */
    @Schema(description = "提交的楼层（自由文本，采纳时写回目标档口 stall.floor）", example = "1F")
    private String floor;

    /**
     * 提交的描述属性（**改动项快照**：仅含用户改动的维度，未改动维度留 NULL）。
     * JSON 对象：键 = 维度 {@code fieldKey}，值 = 机器值 / 数组。
     */
    @Schema(description = "提交的描述属性（JSON：键=维度 fieldKey，值=机器值/数组；仅改动维度，可空）",
            example = "{\"dietType\":\"veg\",\"flavorTags\":[\"spicy\",\"sour\"]}")
    private String attributes;

    /** 提交的菜品图片URL列表（JSON 数组，COS 绝对地址，≤3 张） */
    @TableField(typeHandler = StringListTypeHandler.class)
    @Schema(description = "菜品图片URL列表（COS 绝对地址，≤3 张，可空）")
    private List<String> images;

    /** 处理状态：pending / adopted / rejected */
    @Schema(description = "处理状态：pending/adopted/rejected")
    private String status;

    /** 处理回复（采纳时固定「已采纳，菜品信息已更新」；拒绝时为管理员回复） */
    @Schema(description = "处理回复")
    private String reply;

    /** 不采纳原因（status=rejected 时必填，1~200 字） */
    @Schema(description = "不采纳原因（status=rejected 时非空）")
    private String rejectReason;

    /** 处理时间 */
    @Schema(description = "处理时间")
    private LocalDateTime handledAt;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
