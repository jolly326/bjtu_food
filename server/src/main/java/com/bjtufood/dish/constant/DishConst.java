package com.bjtufood.dish.constant;

/**
 * 菜品模块常量（统一上架状态字面量，避免散落字符串）。
 * <p>
 * 注：菜品审核语义已整体退役，{@code AUDIT_APPROVED} 别名常量与
 * {@code common/constant/AuditStatusConst} 值域真源（仅服务该审核流）已同批删除。
 * 注：标签（{@code tags}）与辣度（{@code spice_level}）整链下线，
 * 其白名单常量（VALID_TAGS / TAG_* / SPICE_LEVEL_*）一并删除。
 */
public interface DishConst {

    /**
     * 管理端列表的**上架状态筛选白名单**（A3，2026-10-03 补）：非法值 `400`。
     * 不传 = 全部（**含已下架** —— 管理端要能看到并维护下架菜品）。
     */
    java.util.Set<String> QUERY_STATUSES = java.util.Set.of("on", "off");

    /** 上架状态：on=在售 */
    String STATUS_ON = "on";

    /** 下架状态：off=已下架（公开查询按 `status='on'` 过滤，等价于「不存在」） */
    String STATUS_OFF = "off";
}
