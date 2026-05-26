package com.bjtufood.common.constant;

/**
 * 用户角色常量
 * <p>
 * 对应 user 表的 role 字段。
 * Spring Security 中角色需加 ROLE_ 前缀，
 * 数据库存储时去掉前缀，仅存 student/canteen_admin/sys_admin。
 */
public interface RoleConst {

    /** 学生用户（小程序端使用） */
    String STUDENT = "student";

    /** 食堂管理员（后台管理端使用，管理本档口菜品和数据） */
    String CANTEEN_ADMIN = "canteen_admin";

    /** 系统管理员（后台管理端使用，管理食堂框架、用户、评价审核） */
    String SYS_ADMIN = "sys_admin";

    // ==================== Spring Security 角色名（带前缀） ====================

    String ROLE_STUDENT = "ROLE_STUDENT";
    String ROLE_CANTEEN_ADMIN = "ROLE_CANTEEN_ADMIN";
    String ROLE_SYS_ADMIN = "ROLE_SYS_ADMIN";
}
