package com.bjtufood.dish.view;

import com.bjtufood.dish.dto.DishViewCondition;
import com.bjtufood.dish.entity.DishFilterView;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 筛选视图行 → SQL 取数参数（{@link DishListQuery}）—— **唯一分派点**。
 *
 * <p>视图行携带全部口径：`conditions`（JSON 条件）与 `sortKind`（排序口径）都落库，
 * 由本类解析为取数参数；字段 / 操作符 / 排序白名单的唯一真源仍是 {@link DishViewConditions}。
 *
 * <p>纯函数、无状态：视图缺失（行不存在）或条件 JSON 非法时返回 {@code null}，
 * 由调用方（Service）抛 `400`，沿用「校验在 Service 层、不落 SQL」的既有口径。
 */
public final class DishViewResolver {

    /**
     * 解析视图行 → Mapper 取数参数。
     *
     * @param view    视图行（{@code null} = 视图不存在 / 未登记，调用方按 `400` 处理）
     * @param keyword 关键词（可空）
     * @param seed    会话随机种子（可空；**仅种子随机序消费**，其余口径不带 seed，避免误导）
     * @return 取数参数；视图缺失或条件非法时返回 {@code null}
     */
    public static DishListQuery resolve(DishFilterView view, String keyword, String seed) {
        if (view == null) {
            return null;
        }
        DishListQuery.SortKind sortKind = DishListQuery.sortKindOf(view.getSortKind());
        if (sortKind == null) {
            return null;
        }
        List<DishViewCondition> conditions;
        try {
            conditions = DishViewConditions.parse(view.getConditions());
        } catch (RuntimeException e) {
            // 条件 JSON 非法 / 白名单越界：不静默降级成「不筛选」，交调用方按 400 处理
            return null;
        }
        // 契约口径（docs/api/client/dishes.md）：命中 keyword 时 seed 不参与排序，
        // 故 keyword 非空时 effectiveSeed 置 null（seed 仅在无 keyword 的种子随机视图下生效）
        String effectiveSeed = sortKind == DishListQuery.SortKind.SEED_RANDOM
                && !StringUtils.hasText(keyword) ? seed : null;
        return new DishListQuery(keyword, effectiveSeed, conditions, sortKind);
    }

    private DishViewResolver() {
    }
}
