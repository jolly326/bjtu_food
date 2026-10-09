package com.bjtufood.auth.dto;

import lombok.Data;

/**
 * 管理端登录态响应（{@code GET /admin/auth/me}）。
 *
 * <p><b>契约真源</b>：{@code docs/api/web/auth.md} §校验当前登录态 —— 回「我是谁 / 角色 / 上次何时登录」，
 * <b>不回 token、不回 expiresIn</b>：token 由端上自己持有，重复下发等于凭空多一个流转副本；
 * 有效期由端上按 JWT 自身的 {@code exp} 判定，无须回读。
 *
 * <p>{@code lastLoginAt} 格式固定 {@code yyyy-MM-dd HH:mm:ss}（不依赖 Jackson 默认策略），
 * 与契约逐字一致。
 *
 * @param username      登录名（供端上身份区回显）
 * @param lastLoginAt   最近登录成功时间；账号从未登录过时为 {@code null}（此时字段不下发）
 * @param role          角色（供端上按角色隐显入口；**真正的门控在服务端**，前端隐显只是体验）
 * @param passwordAging 口令是否已超期（距上次改密 &gt;180 天 ⇒ 端上提示改密，**不强制踢出**）
 */
@Data
public class AdminMeVO {

    private String username;

    private String lastLoginAt;

    private String role;

    private boolean passwordAging;
}
