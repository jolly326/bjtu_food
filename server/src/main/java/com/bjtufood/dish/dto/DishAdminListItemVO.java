package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端菜品**列表行** VO（{@code GET /admin/dishes}，A3「列表 / 详情 VO 分离」）。
 *
 * <p>契约真源：docs/api/web/dishes.md 的「响应 · `DishAdminListItemVO`（列表行 · **瘦身**）」。
 *
 * <p><b>为什么拆分</b>：列表是「扫视」场景（要小、要快），详情是「编辑回填」场景（要全）。
 * 共用 VO 会让列表为 20 行 × 512 字描述 + 属性 Map + 全量图片买单 —— 既浪费带宽，
 * 也让按 {@code updated_at DESC} 排序的分页查询被迫回传大字段。
 *
 * <p><b>封面是派生值</b>：{@code coverImage} 取 {@code images} 首图（SQL 层只取第 0 项，
 * 不在 Java 侧解析整串 JSON）；无图为空串。
 *
 * <p><b>{@code ratingCount} 必须留</b>：它是删除二次确认「将一并删除 N 条评价」的影响面来源。
 *
 * <p><b>🔴 {@code recentViewCount} = 近 30 天浏览量</b>（2026-10-05 新增）：来自
 * {@code dish_view_log} 明细日志的滚动窗口统计（{@code COUNT(*) WHERE viewed_at >= NOW()-30d}），
 * **不是**历史累计的 {@code dish.view_count}（该列已停写，仅作历史参考）。
 * 用途：让运营看出「哪道菜多人看但没评价」⇒ 判断要不要推它做活动 / 引导其产出评价。
 *
 * <p><b>为何不复用 {@code viewCount} 字段名</b>：一个是无时间窗的历史累计（单调递增），
 * 一个是 30 天窗口值（会随时间回落），语义不同 —— 混用会产生「老菜永远高」的错误结论。
 */
@Data
@Schema(description = "管理端菜品列表行（瘦身）")
public class DishAdminListItemVO {

    @Schema(description = "菜品ID")
    private Long id;

    @Schema(description = "所属档口ID（行内跳转用）", example = "3")
    private Long stallId;

    @Schema(description = "档口名（联表带出）", example = "清真面档")
    private String stallName;

    @Schema(description = "食堂名（联表带出）", example = "清真食堂")
    private String canteenName;

    @Schema(description = "菜品名称", example = "牛肉拉面")
    private String name;

    @Schema(description = "现价（分）", example = "1600")
    private Integer price;

    @Schema(description = "原价（分，可空）", example = "2000")
    private Integer originalPrice;

    @Schema(description = "封面图绝对 URL（首图派生；无图为空串）")
    private String coverImage;

    @Schema(description = "分类值 ID（值域 = 分类值字典 /admin/dish-categories）", example = "3")
    private Long mealTypeId;

    @Schema(description = "分类中文名（A6 分类值字典派生，端上零硬编码）", example = "面食粉类")
    private String mealTypeLabel;

    @Schema(description = "上架状态：on / off")
    private String status;

    @Schema(description = "均分（零评价为 null）")
    private BigDecimal avgRating;

    @Schema(description = "评价数（删除确认的影响面来源）", example = "12")
    private Integer ratingCount;

    @Schema(description = "近 30 天浏览量（dish_view_log 滚动窗口统计；非历史累计。窗口内无浏览为 0）",
            example = "86")
    private Long recentViewCount;

    @Schema(description = "更新时间（列表排序依据）")
    private LocalDateTime updatedAt;
}
