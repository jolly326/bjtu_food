package com.bjtufood.dish.view;

import com.bjtufood.dish.entity.DishFilterView;

/**
 * 筛选视图（表行）→ SQL 取数参数（{@link DishListQuery}）—— **唯一分派点**。
 *
 * <p><b>2026-10-03 口径变更（A6 落地）</b>：原实现按代码常量 {@code DishViewConst.Kind} 做
 * {@code switch} 分派（「新增筛选语义要改三处：枚举 + switch + XML 片段」）；现改为**表驱动**：
 * 视图行的 {@code conditions}（字段白名单）与 {@code sort_kind} 翻译为通用取数参数 ⇒
 * **新增视图实例免开发**，只有新增**字段或操作符**才动代码（{@code DishViewConditions} 白名单表一处）。
 *
 * <p>纯函数、无状态：视图缺失 / `sort_kind` 不在白名单时返回 {@code null}，
 * 由调用方（Service）抛 `400`，沿用「校验在 Service 层、不落 SQL」的既有口径。
 */
public final class DishViewResolver {

    /**
     * 解析视图行 → Mapper 取数参数。
     *
     * @param view    视图行（{@code null} = 视图不存在 / 未登记，调用方按 `400` 处理）
     * @param keyword 关键词（可空）
     * @param seed    会话随机种子（可空；**仅推荐类视图消费**，其余口径不带 seed，避免误导）
     * @return 取数参数；视图缺失或排序口径不在白名单时返回 {@code null}
     */
    public static DishListQuery resolve(DishFilterView view, String keyword, String seed) {
        if (view == null) {
            return null;
        }
        DishListQuery.SortKind sortKind = DishListQuery.sortKindOf(view.getSortKind());
        if (sortKind == null) {
            return null;
        }
        String effectiveSeed = sortKind == DishListQuery.SortKind.SEED_RANDOM ? seed : null;
        // conditions 已在写入时校验并落库；此处再解析一次会做同样的白名单校验（防手工改库绕过校验）
        return new DishListQuery(keyword, effectiveSeed,
                DishViewConditions.parse(view.getConditions()), sortKind);
    }

    private DishViewResolver() {
    }
}
