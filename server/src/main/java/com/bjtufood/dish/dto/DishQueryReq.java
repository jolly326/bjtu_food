package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品列表查询参数。
 * <p>
 * 已下线参数：{@code tag}（标签整链删除，2026-09-20）、{@code spiceLevel}（§7.28）、
 * {@code stallId}（2026-09-21 §7.23 第 6 条：筛选只有食堂/价格/大类，无档口入口）、
 * {@code sortBy} / {@code sortOrder}（2026-09-21 §7.33：排序口径收敛为服务端恒热度倒序）。
 */
@Data
@Schema(description = "菜品列表查询参数")
public class DishQueryReq {

    @Schema(description = "页码，从1开始", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "关键词，匹配菜品名/别名或档口名/食堂名", example = "牛肉")
    private String keyword;

    @Schema(description = "食堂ID筛选", example = "1")
    private Long canteenId;

    @Schema(description = "菜品大类筛选（单值，键域=MealTypeConst；白名单校验，非法值 400）", example = "noodle")
    private String mealType;

    @Schema(description = "最低价格，单位：分", example = "1000")
    private Integer minPrice;

    @Schema(description = "最高价格，单位：分", example = "2000")
    private Integer maxPrice;
}
