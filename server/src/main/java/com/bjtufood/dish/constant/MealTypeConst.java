package com.bjtufood.dish.constant;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜品大类常量 —— **唯一真源**（2026-09-21 §7.34 / change home-ui-refresh）。
 * <p>
 * 口径：每个菜品恰属**一个**大类（{@code dish.meal_type} 单值枚举），一个大类可含多个菜品；
 * 大类与属性维度（{@code dietType} / {@code ingredients} / {@code flavorTags} / {@code serveTemp}）
 * **不是一类字段**——属性为多值横切，大类为单值互斥且全量覆盖。
 * <p>
 * 判定口径（后台录入与数据回填的唯一判据）：**按菜名与做法形态判定，不看主料与口味**。
 * <p>
 * 分层：本常量仅供「大类筛选（{@code GET /dishes?mealType=}）」与「大类字典下发
 * （{@code GET /dishes/meal-types}）」使用；**大类不进入公开菜品出参（{@code DishListItemVO} / {@code DishDetailVO}）**，
 * 后台 {@code DishAdminReq} / {@code DishAdminVO} 需要（录入下拉 + 编辑回填 + 列表筛选）。
 * <p>
 * 边界：本枚举**不构成品类复活**——无 {@code category} 表、无 {@code /admin/categories}、
 * 无后台品类维护页（§7.22 第 1 条继续有效）。新增大类须改本常量并发版（低频变更，用户已接受）。
 */
public final class MealTypeConst {

    /** 单个大类定义：枚举值 / 中文标签 / 展示顺序。 */
    public record MealType(String value, String label, int order) {
    }

    /** 全部大类（按 order 升序即声明顺序）。 */
    public static final List<MealType> ALL = List.of(
            new MealType("set_meal", "套餐盖饭", 1),
            new MealType("stir_fry", "家常小炒", 2),
            new MealType("noodle", "面食粉类", 3),
            new MealType("dry_pot", "香锅干锅", 4),
            new MealType("snack", "风味小吃", 5),
            new MealType("soup_drink", "汤饮甜品", 6)
    );

    /** 合法枚举键集合（白名单校验用，PR-06：非法值一律 400，不静默降级）。 */
    public static final Set<String> KEYS = ALL.stream()
            .map(MealType::value)
            .collect(Collectors.toUnmodifiableSet());

    /** 是否为合法大类枚举键。 */
    public static boolean isValid(String key) {
        return key != null && KEYS.contains(key);
    }

    private MealTypeConst() {
    }
}
