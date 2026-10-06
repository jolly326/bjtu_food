package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 后台菜品列表展示信息（管理端专用）。
 * <p>
 * 与公开 VO 的差异：保留 {@code status}（上下架，管理端需处置）、{@code stallId} /
 * {@code createdAt} / {@code updatedAt}（管理端维护用）；<b>不返回 {@code viewCount}</b>
 * （管理端不展示）。
 */
@Data
@Schema(description = "后台菜品列表展示信息")
public class DishAdminVO {

    @Schema(description = "菜品ID")
    private Long id;

    @Schema(description = "所属档口ID")
    private Long stallId;

    @Schema(description = "菜品名称", example = "牛肉拉面")
    private String name;

    @Schema(description = "现价（分，已含折扣）", example = "1200")
    private Integer price;

    @Schema(description = "原价（分，可空）", example = "1500")
    private Integer originalPrice;

    @Schema(description = "菜品描述")
    private String description;

    @Schema(description = "菜品多图URL列表（列 ↔ List 转换由 StringListTypeHandler 在持久层完成；2026-09-23 R5）")
    private List<String> images;

    @Schema(description = "状态（on/off）", example = "on")
    private String status;

    @Schema(description = "平均评分", example = "4.5")
    private BigDecimal avgRating;

    @Schema(description = "评价数")
    private Integer ratingCount;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "档口名称", example = "面食窗口")
    private String stallName;

    @Schema(description = "所属食堂名称", example = "第一食堂")
    private String canteenName;

    /** 描述属性（动态属性模型）：键 = 维度 fieldKey，值 = 中文文本 / 数组；无属性为 null */
    @Schema(description = "描述属性（键=维度 fieldKey，值=中文文本/数组）",
            example = "{\"dietType\":\"半荤\",\"ingredients\":[\"蛋\"],\"flavorTags\":[\"酸\",\"甜\"],\"serveTemp\":\"热食\"}")
    private Map<String, Object> attributes;

    @Schema(description = "菜品分类键（值域 = 分类值字典 /admin/dish-categories；管理端录入下拉 + 编辑回填）", example = "noodle")
    private String mealType;

    /** 分类中文名（A6 分类值字典派生；列表直接可读，端上零硬编码） */
    @Schema(description = "分类中文名", example = "面食粉类")
    private String mealTypeLabel;
}
