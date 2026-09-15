package com.bjtufood.dish.constant;

import java.util.Set;

/**
 * 菜品模块常量（统一上架/标签/辣度字面量，避免散落字符串）
 * <p>
 * 注（2026-09-15 阶段4）：菜品审核语义已整体退役，{@code AUDIT_APPROVED} 别名常量与
 * {@code common/constant/AuditStatusConst} 值域真源（仅服务该审核流）已同批删除。
 */
public interface DishConst {

    /** 上架状态：on=在售（off=下架无独立常量，见 schema.sql dish.status 列注释） */
    String STATUS_ON = "on";

    /**
     * 标签权威值域 —— 单一真源（与 schema.sql dish.tags 列注释、web/src/api/tags.ts TAG_OPTIONS 对齐）。
     * <p>
     * 仅 {@code recommended}（必吃推荐）/ {@code signature}（招牌菜）两值；
     * 写库值必须为英文枚举，小程序端 {@code DishMapper.xml} 的 FIND_IN_SET 筛选依赖一致取值。
     */
    String TAG_RECOMMENDED = "recommended";
    String TAG_SIGNATURE = "signature";

    /** 标签白名单集合（写入校验用，单一真源） */
    Set<String> VALID_TAGS = Set.of(TAG_RECOMMENDED, TAG_SIGNATURE);

    /** 辣度枚举值域（含）：0=不辣 1=微辣 2=中辣 3=重辣（与 schema.sql dish.spice_level 列注释一致） */
    int SPICE_LEVEL_MIN = 0;
    int SPICE_LEVEL_MAX = 3;
}
