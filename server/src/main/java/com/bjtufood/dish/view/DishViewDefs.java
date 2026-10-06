package com.bjtufood.dish.view;

import com.bjtufood.dish.dto.DishViewCondition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页筛选视图的**逻辑真源**（代码常量）—— tab 的**筛选条件 + 排序口径**，按 `key` 索引。
 *
 * <p><b>两层模型</b>：表 `dish_filter_view` 只存**展示态**（文案 / 顺序 / 显隐，后台可改免发版）；
 * 本类存**逻辑**（筛选标准 + 排序口径，固定、非后台可配）。端上回传的 {@code view=<key>} 先在
 * 本类按键解析逻辑，再与表行的展示态合并。
 *
 * <p><b>边界</b>：表行的 `key` 在本类无定义 ⇒ 逻辑缺失 —— 该视图**不下发**（{@code GET /dishes/views}），
 * {@code GET /dishes?view=<该项 key>} 按非法 key 处理为 `400`（由 {@link DishViewResolver} 返回 {@code null}
 * 交由 Service 抛错）。
 *
 * <p><b>内置 7 个视图</b>（{@code recommend} + 6 个物理大类），条件语义与字段 / 操作符白名单见
 * {@link DishViewConditions}；口径真源：docs/api/web/views.md 与
 * docs/schema/dish_filter_view.md。
 *
 * <p>类初始化时对本类全部定义做一次白名单自检（字段 / 操作符走 {@link DishViewConditions#validate}，
 * 排序口径走 {@link DishListQuery#sortKindOf}）—— 常量写错在**启动即失败**，不留到请求期。
 */
public final class DishViewDefs {

    /** 一个视图的逻辑：筛选条件（AND；空列表 = 全部菜品）+ 排序口径（见 {@link DishViewConditions#SORT_KINDS}）。 */
    public record Def(String key, List<DishViewCondition> conditions, String sortKind) {
    }

    private static final Map<String, Def> DEFS = build();

    private DishViewDefs() {
    }

    /** 按键取逻辑定义；`key` 为空白或未定义时返回 {@code null}。 */
    public static Def byKey(String key) {
        return key == null ? null : DEFS.get(key.trim());
    }

    /** 该 `key` 是否有逻辑定义。 */
    public static boolean contains(String key) {
        return byKey(key) != null;
    }

    private static Map<String, Def> build() {
        Map<String, Def> defs = new LinkedHashMap<>();
        defs.put("recommend", new Def("recommend", List.of(), "random"));
        defs.put("set_meal", mealTypeView("set_meal"));
        defs.put("stir_fry", mealTypeView("stir_fry"));
        defs.put("noodle", mealTypeView("noodle"));
        defs.put("dry_pot", mealTypeView("dry_pot"));
        defs.put("snack", mealTypeView("snack"));
        defs.put("soup_drink", mealTypeView("soup_drink"));
        Map<String, Def> frozen = Collections.unmodifiableMap(defs);
        validate(frozen);
        return frozen;
    }

    /**
     * 物理大类视图：{@code mealType = <key>}。
     * <p>
     * 🔴 排序走 {@code random}：冷启动期让每道菜都有机会被看到，优于固定榜单。
     */
    private static Def mealTypeView(String mealType) {
        return new Def(mealType, List.of(mealTypeEq(mealType)), "random");
    }

    private static DishViewCondition mealTypeEq(String value) {
        DishViewCondition c = new DishViewCondition();
        c.setField("mealType");
        c.setOp("=");
        c.setValue(value);
        return c;
    }

    /** 启动自检：任一视图的条件字段 / 操作符 / 排序口径越出白名单即抛错（常量写错属代码缺陷）。 */
    private static void validate(Map<String, Def> defs) {
        for (Def def : defs.values()) {
            DishViewConditions.validate(def.conditions());
            if (DishListQuery.sortKindOf(def.sortKind()) == null) {
                throw new IllegalStateException("DishViewDefs 排序口径不在白名单：" + def.key() + " → " + def.sortKind());
            }
        }
    }
}
