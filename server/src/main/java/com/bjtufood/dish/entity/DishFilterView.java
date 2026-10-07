package com.bjtufood.dish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页筛选视图（表 {@code dish_filter_view}）—— **tab 的全部口径都在本表**。
 * <p>
 * 一个 tab = 文案 + 顺序 + 启停 + **筛选条件**（{@code conditions} 的 JSON 原文）+ **排序口径**
 * （{@code sortKind}）；端上回传 {@code view=<id>} 定位视图。
 * <ul>
 *   <li>{@code conditions}：条件数组的 JSON 原文（`[]` = 不筛选）；解析 / 校验 / 翻译的唯一入口是
 *       {@code DishViewConditions}（字段 / 操作符 / 取值三层白名单），本字段只持存储形态；</li>
 *   <li>无「默认视图」概念（空 {@code view} 取首个启用视图）。</li>
 * </ul>
 * 口径真源：docs/schema/dish_filter_view.md 与 docs/api/web/views.md。
 */
@Data
@TableName("dish_filter_view")
@Schema(description = "首页筛选视图")
public class DishFilterView {

    @TableId(type = IdType.AUTO)
    @Schema(description = "视图ID（端上回传 view=<id>）")
    private Long id;

    @Schema(description = "tab 文案（可改）", example = "面食粉类")
    private String label;

    /** 筛选条件 JSON 原文（`[]` = 不筛选；结构与白名单见 {@code DishViewConditions}） */
    @Schema(description = "筛选条件（JSON 数组；[] = 不筛选）", example = "[{\"field\":\"mealTypeId\",\"op\":\"=\",\"value\":\"3\"}]")
    private String conditions;

    /** 排序口径（白名单：random / priceAsc / priceDesc / discountDesc / ratingDesc / newest） */
    @Schema(description = "排序口径", example = "random")
    private String sortKind;

    /** 展示顺序（升序） */
    @Schema(description = "展示顺序（升序）", example = "4")
    private Integer sortOrder;

    @Schema(description = "是否在 client 首页出现（false=tab 隐藏）")
    private Boolean enabled;

    /** 更新时间（DB 时钟：`ON UPDATE CURRENT_TIMESTAMP`；应用层不写、不填充） */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
