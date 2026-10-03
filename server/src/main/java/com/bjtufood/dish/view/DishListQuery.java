package com.bjtufood.dish.view;

import com.bjtufood.dish.dto.DishViewCondition;

import java.util.List;

/**
 * 视图解析后的 SQL 取数参数（{@code DishMapper} 入参，隔离「视图语义」与「SQL 条件」）。
 * <p>
 * 由 {@link DishViewResolver} 从「表 `dish_filter_view` 的视图行」翻译而来：API 层只认视图
 * {@code key}，SQL 层只认这里的字段，二者不互相泄漏。
 * <p>
 * <b>2026-10-03 口径变更（A6 落地）</b>：原来的 `mealType` / `discountOnly` 两个**固定字段**
 * 换成通用的 {@link DishViewCondition} 列表 —— 筛选语义由「加枚举 + switch 分支 + XML 片段」
 * 改为「**字段白名单 + 条件翻译**」；新增**视图实例**免开发，新增**字段/操作符**才动代码
 * （改动点收敛到 {@code DishViewConditions} 的白名单表一处）。
 *
 * @param keyword    关键词（菜名 / 档口名 / 食堂名三路模糊匹配；可空）
 * @param seed       会话随机种子（仅 {@link SortKind#SEED_RANDOM} 且无 keyword 时参与排序；可空）
 * @param conditions 已校验的筛选条件（**AND**；空列表 = 全部菜品）—— 白名单与参数化由
 *                   {@code DishViewConditions} 保证，本记录只做载体
 * @param sortKind   排序口径（由视图行的 `sort_kind` 决定，端上无排序入口）
 */
public record DishListQuery(
        String keyword,
        String seed,
        List<DishViewCondition> conditions,
        SortKind sortKind) {

    /**
     * 排序口径（与 `dish_filter_view.sort_kind` 的 7 项白名单**一一对应**）。
     */
    public enum SortKind {
        /** 热度倒序（大类 / 搜索等默认口径）。 */
        HEAT,
        /** 会话种子稳定伪随机序（推荐流）。 */
        SEED_RANDOM,
        /** 折扣力度倒序（折扣视图）。 */
        DISCOUNT_DESC,
        /** 现价升序。 */
        PRICE_ASC,
        /** 现价降序。 */
        PRICE_DESC,
        /** 均分降序（评价数作决胜键）。 */
        RATING_DESC,
        /** 上新时间倒序。 */
        NEWEST
    }

    /**
     * `sort_kind` 字符串 → {@link SortKind}（白名单外的值返回 {@code null}，由调用方按 `400` 处理）。
     */
    public static SortKind sortKindOf(String sortKind) {
        if (sortKind == null) {
            return null;
        }
        return switch (sortKind.trim()) {
            case "heat" -> SortKind.HEAT;
            case "random" -> SortKind.SEED_RANDOM;
            case "discountDesc" -> SortKind.DISCOUNT_DESC;
            case "priceAsc" -> SortKind.PRICE_ASC;
            case "priceDesc" -> SortKind.PRICE_DESC;
            case "ratingDesc" -> SortKind.RATING_DESC;
            case "newest" -> SortKind.NEWEST;
            default -> null;
        };
    }
}
