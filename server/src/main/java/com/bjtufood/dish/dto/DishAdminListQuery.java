package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理端菜品列表的**筛选条件**（{@code GET /admin/dishes}，A3）。
 *
 * <p>契约真源：docs/api/web/dishes.md 的「请求参数（`GET /admin/dishes`）」。
 * <p>排序固定 `updatedAt DESC`（最近维护在前），端上无排序入口。
 * <p>抽成 record 而不是继续加方法参数：筛选维度后续还会增长，签名会失控；
 * 且 Mapper 侧以 `@Param("q")` 引用，SQL 里读 `q.stallId` 与既有 {@code DishListQuery} 口径一致。
 *
 * @param stallId     按档口筛选（可空）
 * @param canteenId   按食堂筛选（**经档口间接**：`s.canteen_id`，可空）
 * @param mealTypeId  按种类筛选（可空；值域 = A6 种类字典（系统维度取值）的 `id`）
 * @param status      按上架状态筛选（`on` / `off`；不传 = 全部，**含已下架**）
 * @param keyword     关键词（菜名 / 档口名 / 食堂名，与 client 搜索同口径；可空）
 */
@Schema(description = "管理端菜品列表筛选条件")
public record DishAdminListQuery(
        Long stallId,
        Long canteenId,
        Long mealTypeId,
        String status,
        String keyword) {
}
