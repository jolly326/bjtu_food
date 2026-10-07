package com.bjtufood.dish.view;

import com.bjtufood.dish.entity.DishFilterView;
import org.springframework.util.StringUtils;

/**
 * 筛选视图（表行 + {@link DishViewDefs} 逻辑）→ SQL 取数参数（{@link DishListQuery}）—— **唯一分派点**。
 *
 * <p>表行只承载**展示态**（文案 / 顺序 / 显隐）；筛选条件与排序口径按 `key` 从代码常量
 * {@link DishViewDefs} 取。字段 / 操作符 / 排序白名单的唯一真源仍是 {@link DishViewConditions}。
 *
 * <p>纯函数、无状态：视图缺失（表行未登记）或逻辑无定义（`key` 不在 {@code DishViewDefs}）时返回
 * {@code null}，由调用方（Service）抛 `400`，沿用「校验在 Service 层、不落 SQL」的既有口径。
 */
public final class DishViewResolver {

    /**
     * 解析视图行 → Mapper 取数参数。
     *
     * @param view    视图行（{@code null} = 视图不存在 / 未登记，调用方按 `400` 处理）
     * @param keyword 关键词（可空）
     * @param seed    会话随机种子（可空；**仅推荐类视图消费**，其余口径不带 seed，避免误导）
     * @return 取数参数；视图缺失或逻辑无定义时返回 {@code null}
     */
    public static DishListQuery resolve(DishFilterView view, String keyword, String seed) {
        if (view == null) {
            return null;
        }
        DishViewDefs.Def def = DishViewDefs.byKey(view.getKey());
        if (def == null) {
            return null;
        }
        DishListQuery.SortKind sortKind = DishListQuery.sortKindOf(def.sortKind());
        if (sortKind == null) {
            return null;
        }
        // 契约口径（docs/api/client/dishes.md）：命中 keyword 时 seed 不参与排序，
        // 故 keyword 非空时 effectiveSeed 置 null（seed 仅在无 keyword 的 SEED_RANDOM 视图下生效）
        String effectiveSeed = sortKind == DishListQuery.SortKind.SEED_RANDOM
                && !StringUtils.hasText(keyword) ? seed : null;
        // 条件取自代码常量 DishViewDefs（类初始化时已过白名单），直接使用
        return new DishListQuery(keyword, effectiveSeed, def.conditions(), sortKind);
    }

    private DishViewResolver() {
    }
}
