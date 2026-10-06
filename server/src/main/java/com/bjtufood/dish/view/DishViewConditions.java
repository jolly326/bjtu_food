package com.bjtufood.dish.view;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.dish.dto.DishViewCondition;
import com.bjtufood.dish.entity.Dish;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 视图筛选条件引擎 —— **字段 / 操作符 / 值 三层白名单**的唯一真源。
 *
 * <p>契约真源：docs/api/web/views.md 的「筛选字段白名单」与
 * docs/schema/dish_filter_view.md 的「条件语言是有限语言（安全底线）」。
 *
 * <p><b>为什么必须白名单</b>：开放「可配置 SQL」等于把 ORM 暴露给运营 —— 注入与性能双双失控。
 * 本类只把**已知字段 + 已知操作符**翻译为**参数化**条件；任何白名单外的输入一律 `400`。
 *
 * <p><b>组合关系只有 AND</b>（一期不支持 OR）。
 *
 * <p>本类同时服务三处：① 管理端列表的 `matchedCount`；② 预览端点的试算；
 * ③ 学生端列表的取数（{@link #toWrapper} 与 {@code DishMapper.xml} 的 {@code <foreach>} 同口径）。
 */
public final class DishViewConditions {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 排序口径白名单（`sortKind`）。
     * <p>
     * 🔴 **无 {@code "heat"}**：MVP 期 7 个视图全部走 {@code random}，无热度排序需求
     * （见 P0-3 评审）。后期若重启须重新拍板并**必须含浏览量封顶**。
     */
    public static final Set<String> SORT_KINDS = Set.of(
            "priceAsc", "priceDesc", "discountDesc", "ratingDesc", "newest", "random");

    /** 字段 → 允许的操作符（白名单表；**这是新增筛选语义的唯一改动点**）。 */
    private static final Map<String, Set<String>> ALLOWED_OPS = Map.of(
            "mealType", Set.of("=", "in"),
            "discount", Set.of("isTrue"),
            "price", Set.of("between", ">=", "<="),
            "stallId", Set.of("=", "in"),
            "canteenId", Set.of("=", "in"),
            "avgRating", Set.of(">="),
            "createdAt", Set.of("withinDays"));

    /** 无需取值的操作符（`discount.isTrue` 派生 `original_price > price`）。 */
    private static final Set<String> VALUELESS_OPS = Set.of("isTrue");

    private DishViewConditions() {
    }

    /**
     * 解析并校验条件 JSON（`NULL` / 空 / `[]` = 全部菜品）。
     *
     * @param json 条件 JSON 原文（可空）
     * @return 已校验的条件列表（**永不为 null**）
     * @throws BusinessException code=400 字段或操作符不在白名单 / 取值形态非法
     */
    public static List<DishViewCondition> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<DishViewCondition> raw;
        try {
            raw = JSON.readValue(json, new TypeReference<List<DishViewCondition>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("筛选条件格式非法");
        }
        return validate(raw);
    }

    /**
     * 校验条件列表（管理端保存与预览共用）。
     *
     * @throws BusinessException code=400 字段或操作符不在白名单 / 取值形态非法
     */
    public static List<DishViewCondition> validate(List<DishViewCondition> conditions) {
        List<DishViewCondition> result = new ArrayList<>();
        if (conditions == null) {
            return result;
        }
        for (DishViewCondition c : conditions) {
            if (c == null || c.getField() == null) {
                throw new BusinessException("筛选条件缺少字段");
            }
            String field = c.getField().trim();
            String op = c.getOp() == null ? "" : c.getOp().trim();
            Set<String> allowed = ALLOWED_OPS.get(field);
            if (allowed == null) {
                throw new BusinessException("筛选字段不在白名单：" + field);
            }
            if (!allowed.contains(op)) {
                throw new BusinessException("操作符不在白名单：" + field + " " + op);
            }
            if (!VALUELESS_OPS.contains(op) && isEmptyValue(c)) {
                throw new BusinessException("筛选条件缺少取值：" + field);
            }
            requireValuesWellFormed(field, op, c);
            DishViewCondition normalized = new DishViewCondition();
            normalized.setField(field);
            normalized.setOp(op);
            normalized.setValue(c.getValue() == null ? null : c.getValue().trim());
            normalized.setValues(c.getValues() == null ? null : new ArrayList<>(c.getValues()));
            result.add(normalized);
        }
        return result;
    }

    /** 序列化为 JSON 原文（入库口径；空条件写 NULL 由调用方决定）。 */
    public static String toJson(List<DishViewCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(conditions);
        } catch (Exception e) {
            throw new BusinessException("筛选条件序列化失败");
        }
    }

    /**
     * 翻译为条件包装器（**仅 AND**；计数与抽样共用）。
     * <p>
     * 全部取值经 {@code require*} 校验后**参数化**绑定；`canteenId` 用子查询
     * （`stall_id IN (SELECT id FROM stall WHERE canteen_id = ?)`）避免为计数引入联表。
     *
     * @param conditions 已校验的条件（{@link #parse} / {@link #validate} 的产物）
     * @param onSaleOnly 是否只统计在售（菜单列表与匹配数均为 true）
     */
    public static QueryWrapper<Dish> toWrapper(List<DishViewCondition> conditions, boolean onSaleOnly) {
        QueryWrapper<Dish> wrapper = new QueryWrapper<>();
        if (onSaleOnly) {
            wrapper.eq("status", "on");
        }
        for (DishViewCondition c : conditions) {
            switch (c.getField()) {
                case "mealType" -> applyInOrEq(wrapper, "meal_type", c, false);
                case "stallId" -> applyInOrEq(wrapper, "stall_id", c, true);
                case "canteenId" -> {
                    if ("in".equals(c.getOp())) {
                        wrapper.inSql("stall_id", "SELECT id FROM stall WHERE canteen_id IN (" + joinNumbers(c.getValues()) + ")");
                    } else {
                        wrapper.inSql("stall_id", "SELECT id FROM stall WHERE canteen_id = " + requireLong(c.getValue()));
                    }
                }
                case "discount" -> wrapper.apply("original_price IS NOT NULL AND original_price > price");
                case "price" -> {
                    if ("between".equals(c.getOp())) {
                        wrapper.ge("price", requireLong(c.getValues().get(0)));
                        wrapper.le("price", requireLong(c.getValues().get(1)));
                    } else if (">=".equals(c.getOp())) {
                        wrapper.ge("price", requireLong(c.getValue()));
                    } else {
                        wrapper.le("price", requireLong(c.getValue()));
                    }
                }
                case "avgRating" -> wrapper.ge("avg_rating", requireDecimal(c.getValue()));
                case "createdAt" -> wrapper.apply("created_at >= DATE_SUB(NOW(), INTERVAL {0} DAY)",
                        requirePositiveInt(c.getValue()));
                default -> throw new BusinessException("筛选字段不在白名单：" + c.getField());
            }
        }
        return wrapper;
    }

    private static void applyInOrEq(QueryWrapper<Dish> wrapper, String column, DishViewCondition c, boolean numeric) {
        if ("in".equals(c.getOp())) {
            if (numeric) {
                wrapper.inSql(column, "SELECT id FROM stall WHERE id IN (" + joinNumbers(c.getValues()) + ")");
            } else {
                wrapper.in(column, c.getValues());
            }
        } else if (numeric) {
            wrapper.eq(column, requireLong(c.getValue()));
        } else {
            wrapper.eq(column, c.getValue());
        }
    }

    /** `in` / `between` 的取值个数与形态校验（其余在 translate 时按字段类型再校验一次）。 */
    private static void requireValuesWellFormed(String field, String op, DishViewCondition c) {
        if ("in".equals(op) && (c.getValues() == null || c.getValues().isEmpty())) {
            throw new BusinessException("筛选条件缺少取值列表：" + field);
        }
        if ("between".equals(op) && (c.getValues() == null || c.getValues().size() != 2)) {
            throw new BusinessException("between 需要恰好两个取值：" + field);
        }
        if (Set.of("stallId", "canteenId", "price").contains(field)) {
            if ("in".equals(op)) {
                c.getValues().forEach(v -> requireLong(v));
            } else if ("between".equals(op)) {
                requireLong(c.getValues().get(0));
                requireLong(c.getValues().get(1));
            } else if (!"isTrue".equals(op)) {
                requireLong(c.getValue());
            }
        }
        if ("avgRating".equals(field)) {
            requireDecimal(c.getValue());
        }
        if ("createdAt".equals(field)) {
            requirePositiveInt(c.getValue());
        }
    }

    private static boolean isEmptyValue(DishViewCondition c) {
        boolean noSingle = c.getValue() == null || c.getValue().isBlank();
        boolean noList = c.getValues() == null || c.getValues().isEmpty();
        return noSingle && noList;
    }

    private static Long requireLong(String text) {
        try {
            return Long.valueOf(text.trim());
        } catch (Exception e) {
            throw new BusinessException("筛选取值必须是整数：" + text);
        }
    }

    private static Double requireDecimal(String text) {
        try {
            return Double.valueOf(text.trim());
        } catch (Exception e) {
            throw new BusinessException("筛选取值必须是数值：" + text);
        }
    }

    private static Integer requirePositiveInt(String text) {
        int n = requireLong(text).intValue();
        if (n <= 0) {
            throw new BusinessException("天数必须为正整数：" + text);
        }
        return n;
    }

    /** 数字列表拼为 `a,b,c`（每个元素都已过 {@code requireLong} ⇒ 无注入面）。 */
    private static String joinNumbers(List<String> values) {
        List<String> parts = new ArrayList<>(values.size());
        for (String v : values) {
            parts.add(String.valueOf(requireLong(v)));
        }
        return String.join(",", parts);
    }
}
