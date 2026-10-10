package com.bjtufood.dish.view;

/**
 * 视图解析后的 SQL 取数参数（{@code DishMapper} 入参，隔离「视图语义」与「SQL 条件」）。
 * <p>
 * 由 Service 依「表 {@code dish_filter_view} 的视图行」翻译而来：API 层只认视图 {@code id}，
 * SQL 层只认这里的字段，二者不互相泄漏。取数口径固定为
 * 「{@code status='on'} + 种类过滤（{@code categoryId} 为 {@code NULL} 不按种类过滤）
 * + {@code CRC32(CONCAT(seed,'-',id)), id} 随机序」。
 *
 * @param keyword    关键词（菜名 / 档口名 / 食堂名三路模糊匹配；可空）
 * @param seed       会话随机种子（随机序输入；Service 侧保证非空）
 * @param categoryId 归属种类（值域 = {@code dish_category.id}；{@code null} = 不按种类过滤）
 */
public record DishListQuery(
        String keyword,
        String seed,
        Long categoryId) {
}
