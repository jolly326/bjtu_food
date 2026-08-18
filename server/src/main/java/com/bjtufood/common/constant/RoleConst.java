package com.bjtufood.common.constant;

/**
 * 用户角色常量
 * <p>
 * 对应 user 表的 role 字段。
 * Spring Security 中角色需加 ROLE_ 前缀，
 * 数据库存储时去掉前缀，仅存 student/admin。
 */
public interface RoleConst {

    /** 学生（小程序端使用） */
    String STUDENT = "student";

    /** 管理员（浏览器管理端使用） */
    String ADMIN = "admin";

    /** 超级管理员（可管理 ADMIN 账号，仅此角色可访问 /admin/admins/**） */
    String SUPER_ADMIN = "super_admin";

    // ==================== Spring Security 角色名（带前缀） ====================

    String ROLE_STUDENT = "ROLE_STUDENT";
    String ROLE_ADMIN = "ROLE_ADMIN";
    String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    /** 是否为管理端角色（admin / super_admin） */
    static boolean isAdmin(String role) {
        return ADMIN.equals(role) || SUPER_ADMIN.equals(role);
    }
}
