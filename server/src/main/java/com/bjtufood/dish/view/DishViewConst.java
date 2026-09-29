package com.bjtufood.dish.view;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 首页筛选视图常量 —— <b>唯一真源</b>（硬编码；不建字典表、不做运行时配置）。
 * <p>
 * <b>为什么叫 view 而不是 mealType</b>：横向筛选栏上的一个按钮表达的是「<b>用户想看什么</b>」这一
 * <b>导航视角</b>，与「<b>菜品是什么</b>」的入库字段（{@code dish.meal_type}）不是一回事。
 * 物理大类只是「按 {@code meal_type} 取数」的一类视图；将来「折扣」等视图按别的口径取数
 * （价格派生），同一个 {@code view} 参数位即可承载，端上无须改动。
 * <p>
 * <b>视图 ≠ 数据字段</b>：{@link View#key()} 是视图标识（端上回传），{@link View#param()} 才是底层取数参数
 * （{@link Kind#MEAL_TYPE} 时 = {@code dish.meal_type} 值）。二者通常相同，但分开后可以做到
 * 「两个按钮指向同一个取数条件、只是标签 / 排序不同」。
 * <p>
 * <b>扩展方式</b>（新增筛选<b>语义</b>只动三处）：
 * <ol>
 *   <li>本类 {@link Kind} 加一个取值；</li>
 *   <li>{@link DishViewResolver} 的 switch 加一个分支；</li>
 *   <li>{@code DishMapper.xml} 加对应的 WHERE / ORDER BY 片段。</li>
 * </ol>
 * 新增<b>同类视图</b>（如新的大类）则只需在 {@link #ALL} 加一行 + 种子里给菜打 {@code meal_type}。
 * <p>
 * <b>与 {@code dish.meal_type} 的边界</b>：{@code meal_type} 仍是菜品入库字段（后台
 * {@code DishAdminReq.mealType} 用它），其值域由本类 {@link #mealTypeValues()} 派生 ——
 * 不另立常量，保持单一真源。
 */
public final class DishViewConst {

    /** 视图取数形态：决定服务端用哪段筛选条件与排序口径。 */
    public enum Kind {
        /** 无筛选 + 会话种子伪随机序（「为你推荐」）。 */
        RECOMMEND,
        /** 按菜品大类等值筛 + 热度倒序（6 个物理大类；{@code param} = {@code dish.meal_type} 值）。 */
        MEAL_TYPE,
        /** 有折扣 + 折扣力度倒序（扩展位：实现时补 Resolver 分支与 Mapper 片段即可）。 */
        DISCOUNT
    }

    /**
     * 一个筛选视图。
     *
     * @param key   视图标识（端上点击后原样回传 {@code view=<key>}）
     * @param label 中文标签（服务端唯一真源，端上零文案）
     * @param kind  取数形态
     * @param param 取数参数（{@code MEAL_TYPE} 时 = {@code dish.meal_type} 值；其余为 null）
     */
    public record View(String key, String label, Kind kind, String param) {
    }

    /** 默认视图键（请求未带 {@code view} 时落在此视图）。 */
    public static final String DEFAULT_KEY = "recommend";

    /** 全部视图（<b>声明序 = 标签栏展示序</b>，即唯一顺序真源）。 */
    public static final List<View> ALL = List.of(
            new View("recommend", "为你推荐", Kind.RECOMMEND, null),
            new View("set_meal", "套餐盖饭", Kind.MEAL_TYPE, "set_meal"),
            new View("stir_fry", "家常小炒", Kind.MEAL_TYPE, "stir_fry"),
            new View("noodle", "面食粉类", Kind.MEAL_TYPE, "noodle"),
            new View("dry_pot", "香锅干锅", Kind.MEAL_TYPE, "dry_pot"),
            new View("snack", "风味小吃", Kind.MEAL_TYPE, "snack"),
            new View("soup_drink", "汤饮甜品", Kind.MEAL_TYPE, "soup_drink")
    );

    /**
     * 解析视图键 → 视图定义。
     * <p>
     * {@code null} / 空白 → 默认视图（{@link #DEFAULT_KEY}）；未登记的键 → {@code null}
     * （由调用方按白名单非法值抛 400，不静默降级）。
     *
     * @param key 视图键（可空）
     * @return 视图定义；未登记时为 {@code null}
     */
    public static View resolve(String key) {
        String target = (key == null || key.isBlank()) ? DEFAULT_KEY : key;
        return ALL.stream().filter(v -> v.key().equals(target)).findFirst().orElse(null);
    }

    /** 是否为合法视图键（白名单；空值视为合法——落默认视图）。 */
    public static boolean isValid(String key) {
        return resolve(key) != null;
    }

    /** 后台录入可用的 {@code meal_type} 值域（由 {@link Kind#MEAL_TYPE} 视图派生，单一真源）。 */
    public static Set<String> mealTypeValues() {
        return ALL.stream()
                .filter(v -> v.kind() == Kind.MEAL_TYPE)
                .map(View::param)
                .collect(Collectors.toUnmodifiableSet());
    }

    private DishViewConst() {
    }
}
