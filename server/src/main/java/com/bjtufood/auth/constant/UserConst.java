package com.bjtufood.auth.constant;

/**
 * 用户账号状态常量（统一字面量，避免 status 字符串散落各 Service）。
 */
public interface UserConst {

    /** 正常态：游客 / 已认证账号均为此态 */
    String STATUS_ACTIVE = "active";

    /** 被管理员禁用：存量 token 实时拦截 UGC 写，且不可登录 */
    String STATUS_DISABLED = "disabled";

    /** 已注销（匿名化终态）：不可重新登录、不可 UGC 写 */
    String STATUS_DELETED = "deleted";
}
