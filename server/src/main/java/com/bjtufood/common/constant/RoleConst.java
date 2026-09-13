package com.bjtufood.common.constant;

/**
 * 用户角色常量
 * <p>
 * 对应 user 表的 role 字段。**2026-09-13 定型：管理端单一使用者、无角色层级（已移除 super_admin）** ——
 * 管理端不再有登录与角色体系，接口统一由 {@code AdminTokenFilter} 的口令（{@code ADMIN_TOKEN}）校验保护；
 * role 仅保留两层数据语义，用于区分账号归属：{@code student}（小程序端）/ {@code admin}（管理端账号标记）。
 * <p>
 * Spring Security 角色名需 {@code ROLE_} 前缀，数据库存储时去掉前缀。
 */
public interface RoleConst {

    /** 学生（小程序端使用） */
    String STUDENT = "student";

    /** 管理员（管理端账号数据标记；不再用于权限分级） */
    String ADMIN = "admin";

    // ==================== Spring Security 角色名（带前缀） ====================

    String ROLE_STUDENT = "ROLE_STUDENT";
    String ROLE_ADMIN = "ROLE_ADMIN";

    /** 是否为管理端账号（仅剩 admin 一级） */
    static boolean isAdmin(String role) {
        return ADMIN.equals(role);
    }
}
