package com.bjtufood.dish.view;

/**
 * 视图解析后的 SQL 取数参数（{@code DishMapper} 入参，隔离「视图语义」与「SQL 条件」）。
 * <p>
 * 由 {@link DishViewResolver} 从「API 的 {@code view} 键」翻译而来：API 层只认视图 key，
 * SQL 层只认这里的字段，二者不互相泄漏。新增视图语义时本类不需要改动
 * （除非新语义引入了新的取数维度，则加一个字段）。
 *
 * @param keyword      关键词（菜名 / 档口名 / 食堂名三路模糊匹配；可空）
 * @param seed         会话随机种子（仅 {@link SortKind#SEED_RANDOM} 且无 keyword 时参与排序；可空）
 * @param mealType     {@code dish.meal_type} 等值条件（仅大类视图非空）
 * @param discountOnly 是否只取有折扣的菜（原价 &gt; 现价）
 * @param sortKind     排序口径（由视图的 {@link DishViewConst.Kind} 决定，端上无排序入口）
 */
public record DishListQuery(
        String keyword,
        String seed,
        String mealType,
        boolean discountOnly,
        SortKind sortKind) {

    /** 排序口径。 */
    public enum SortKind {
        /** 热度倒序（大类 / 搜索等）。 */
        HEAT,
        /** 会话种子稳定伪随机序（推荐流）。 */
        SEED_RANDOM,
        /** 折扣力度倒序（折扣视图）。 */
        DISCOUNT_DESC
    }
}
