package com.bjtufood.dish.view;

/**
 * 筛选视图 → SQL 取数参数（<b>唯一分派点</b>）。
 * <p>
 * 「新增一种筛选语义」只改三处：{@link DishViewConst.Kind} 加取值 / 本类 switch 加分支 /
 * {@code DishMapper.xml} 加片段；<b>端上永远零改动</b>（端上只认视图 key 与 label）。
 * <p>
 * 纯函数、无状态：白名单非法值返回 {@code null}，由调用方（Service）抛 400，沿用
 * 「校验在 Service 层、不落 SQL」的既有口径。
 */
public final class DishViewResolver {

    /**
     * 解析视图键 → Mapper 取数参数。
     *
     * @param viewKey 视图键（空 = 默认视图；未登记 = 非法）
     * @param keyword 关键词（可空）
     * @param seed    会话随机种子（可空；仅推荐类视图消费）
     * @return 取数参数；视图键未登记时返回 {@code null}（调用方按 400 处理）
     */
    public static DishListQuery resolve(String viewKey, String keyword, String seed) {
        DishViewConst.View view = DishViewConst.resolve(viewKey);
        if (view == null) {
            return null;
        }
        return switch (view.kind()) {
            // 为你推荐：无筛选、会话种子伪随机序
            case RECOMMEND -> new DishListQuery(keyword, seed, null, false,
                    DishListQuery.SortKind.SEED_RANDOM);
            // 物理大类：meal_type 等值筛、热度倒序
            case MEAL_TYPE -> new DishListQuery(keyword, null, view.param(), false,
                    DishListQuery.SortKind.HEAT);
            // 折扣：原价 > 现价、折扣力度倒序
            case DISCOUNT -> new DishListQuery(keyword, null, null, true,
                    DishListQuery.SortKind.DISCOUNT_DESC);
        };
    }

    private DishViewResolver() {
    }
}
