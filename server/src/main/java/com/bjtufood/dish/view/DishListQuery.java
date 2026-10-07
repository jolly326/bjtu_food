package com.bjtufood.dish.view;

import com.bjtufood.dish.dto.DishViewCondition;

import java.util.List;

/**
 * 视图解析后的 SQL 取数参数（{@code DishMapper} 入参，隔离「视图语义」与「SQL 条件」）。
 * <p>
 * 由 {@link DishViewResolver} 依「表 `dish_filter_view` 的视图行（条件 + 排序口径同样落库）」
 * 翻译而来：API 层只认视图 {@code id}，SQL 层只认这里的字段，二者不互相泄漏。
 * <p>
 * 筛选语义为「**字段白名单 + 条件翻译**」：新增**视图实例**只需在后台新建一行（免发版），
 * 新增**字段 / 操作符**才动代码（改动点收敛到 {@code DishViewConditions} 的白名单表一处）。
 *
 * @param keyword    关键词（菜名 / 档口名 / 食堂名三路模糊匹配；可空）
 * @param seed       会话随机种子（仅 {@link SortKind#SEED_RANDOM} 且无 keyword 时参与排序；可空）
 * @param conditions 筛选条件（**AND**；空列表 = 全部菜品）—— 取自视图行的 `conditions`，
 *                   白名单与参数化由 {@code DishViewConditions} 保证，本记录只做载体
 * @param sortKind   排序口径（视图行的 `sortKind`，端上无排序入口）
 */
public record DishListQuery(
        String keyword,
        String seed,
        List<DishViewCondition> conditions,
        SortKind sortKind) {

    /**
     * 排序口径（与 {@code DishViewConditions.SORT_KINDS} 白名单**一一对应**）。
     * <p>
     * 🔴 **无 {@code HEAT}**：浏览量无上限，参与排序即可被低成本刷量霸榜；
     * 如需重启热度排序，须重新拍板并**必须含浏览量封顶**。
     */
    public enum SortKind {
        /**
         * 会话种子稳定伪随机序。
         * <p>
         * 🔴 **种子里 7 个视图全部走这一口径**：无「热度排序」需求。
         * 冷启动期让每道菜都有机会被看到，随机优于固定榜单。
         */
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
     * `sortKind` 字符串 → {@link SortKind}（白名单外的值返回 {@code null}，由调用方按 `400` 处理）。
     */
    public static SortKind sortKindOf(String sortKind) {
        if (sortKind == null) {
            return null;
        }
        return switch (sortKind.trim()) {
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
