package com.bjtufood.dish.constant;

import java.util.List;

/**
 * 菜品描述四维常量 —— **唯一真源**（2026-09-23 §7.40 R4 / R13）。
 * <p>
 * 四维：{@code dietType}（荤素 / 饮食属性，单选）、{@code ingredients}（主料 / 食材，多值）、
 * {@code flavorTags}（口味，多值）、{@code serveTemp}（冷热，单选）。
 * <p>
 * <b>为何在此集中而非各端硬编码</b>：改动前两端**各硬编码一份**「机器值 → 中文」映射表
 * （client {@code api/dish.ts}、web {@code constants/index.ts}，后者还兼作表单选项），
 * 连同后端常量共成三套真源（违反 PR-12）。现由本常量经 {@code GET /dishes/attributes}
 * 下发，两端共用、**零硬编码**。
 * <p>
 * <b>关键约束（R13）</b>：{@link Attribute#field()} 的取值 **MUST 与菜品出参字段名逐字一致**
 * （{@code dietType} / {@code ingredients} / {@code flavorTags} / {@code serveTemp}），
 * 消费方据此**直接匹配**渲染，SHALL NOT 另建「字典 field → VO 字段」的第二套映射。
 * <p>
 * <b>业务数据形态不变</b>：菜品出参仍下发**机器值**（{@code dietType: "meat"}、
 * {@code ingredients: ["chicken"]}），中文仅作展示 —— 保留筛选 / 统计锚点。
 * 值域对应关系的历史真源见 {@code docs/project_spec.md} §7.28。
 * <p>
 * <b>不建字典表</b>：与 {@link MealTypeConst} 同策略 —— 常量即真源，不设外键、不提供后台自助增删；
 * 新增取值须改本常量并发版（低频变更）。
 */
public final class DishAttributeConst {

    /** 单个字典项：维度字段名 / 机器值 / 中文标签 / 组内展示顺序。 */
    public record Attribute(String field, String value, String label, int order) {
    }

    /** 维度字段名常量（与菜品出参字段名逐字一致，见类注释 R13 约束）。 */
    public static final String FIELD_DIET_TYPE = "dietType";
    public static final String FIELD_INGREDIENTS = "ingredients";
    public static final String FIELD_FLAVOR_TAGS = "flavorTags";
    public static final String FIELD_SERVE_TEMP = "serveTemp";

    /** 荤素 / 饮食属性（单选；原 {@code region='清真'} 的饮食约束语义迁入此处）。 */
    public static final List<Attribute> DIET_TYPES = List.of(
            new Attribute(FIELD_DIET_TYPE, "meat", "荤", 1),
            new Attribute(FIELD_DIET_TYPE, "half", "半荤", 2),
            new Attribute(FIELD_DIET_TYPE, "veg", "素", 3),
            new Attribute(FIELD_DIET_TYPE, "halal", "清真", 4)
    );

    /** 主料 / 食材（多值）。 */
    public static final List<Attribute> INGREDIENTS = List.of(
            new Attribute(FIELD_INGREDIENTS, "pork", "猪", 1),
            new Attribute(FIELD_INGREDIENTS, "beef", "牛", 2),
            new Attribute(FIELD_INGREDIENTS, "lamb", "羊", 3),
            new Attribute(FIELD_INGREDIENTS, "chicken", "鸡", 4),
            new Attribute(FIELD_INGREDIENTS, "duck", "鸭", 5),
            new Attribute(FIELD_INGREDIENTS, "fish", "鱼虾", 6),
            new Attribute(FIELD_INGREDIENTS, "egg", "蛋", 7),
            new Attribute(FIELD_INGREDIENTS, "tofu", "豆制品", 8),
            new Attribute(FIELD_INGREDIENTS, "mushroom", "菌菇", 9),
            new Attribute(FIELD_INGREDIENTS, "veg", "青菜", 10),
            new Attribute(FIELD_INGREDIENTS, "noodle", "面", 11),
            new Attribute(FIELD_INGREDIENTS, "rice", "米", 12)
    );

    /** 口味（多值；吸收原「辣度」语义）。 */
    public static final List<Attribute> FLAVOR_TAGS = List.of(
            new Attribute(FIELD_FLAVOR_TAGS, "spicy", "辣", 1),
            new Attribute(FIELD_FLAVOR_TAGS, "numbing", "麻", 2),
            new Attribute(FIELD_FLAVOR_TAGS, "sour", "酸", 3),
            new Attribute(FIELD_FLAVOR_TAGS, "sweet", "甜", 4),
            new Attribute(FIELD_FLAVOR_TAGS, "salty", "咸", 5),
            new Attribute(FIELD_FLAVOR_TAGS, "umami", "鲜", 6),
            new Attribute(FIELD_FLAVOR_TAGS, "light", "清淡", 7),
            new Attribute(FIELD_FLAVOR_TAGS, "heavy", "重口", 8)
    );

    /** 冷热（单选）。 */
    public static final List<Attribute> SERVE_TEMPS = List.of(
            new Attribute(FIELD_SERVE_TEMP, "hot", "热食", 1),
            new Attribute(FIELD_SERVE_TEMP, "room", "常温", 2),
            new Attribute(FIELD_SERVE_TEMP, "ice", "冰", 3)
    );

    /**
     * 全量字典（下发顺序）：按维度分组、组内按 {@code order} 升序。
     * <p>
     * 与 {@link MealTypeConst} 的关键差异：本字典**下发全部取值**，不按库中是否有菜品过滤 ——
     * 四维是「描述属性」，管理端录入表单需要完整选项（大类是「筛选维度」，才做在售过滤）。
     */
    public static final List<Attribute> ALL = java.util.stream.Stream
            .of(DIET_TYPES, INGREDIENTS, FLAVOR_TAGS, SERVE_TEMPS)
            .flatMap(List::stream)
            .toList();

    private DishAttributeConst() {
    }
}
