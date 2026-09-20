package com.bjtufood.dish.constant;

/**
 * 菜品模块常量（统一上架状态字面量，避免散落字符串）。
 * <p>
 * 注（2026-09-15 阶段4）：菜品审核语义已整体退役，{@code AUDIT_APPROVED} 别名常量与
 * {@code common/constant/AuditStatusConst} 值域真源（仅服务该审核流）已同批删除。
 * 注（2026-09-20 拍板）：标签（{@code tags}）与辣度（{@code spice_level}）整链下线，
 * 其白名单常量（VALID_TAGS / TAG_* / SPICE_LEVEL_*）一并删除。
 */
public interface DishConst {

    /** 上架状态：on=在售（off=下架无独立常量，见 schema.sql dish.status 列注释） */
    String STATUS_ON = "on";
}
