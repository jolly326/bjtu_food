package com.bjtufood.auth.dto;

import lombok.Data;

/**
 * 管理端登录响应（{@code POST /admin/auth/login}）。
 *
 * <p>🔴 <b>只回 token，不回口令、不回哈希</b>：前端凭此 token 维持登录态，
 * 无需（也不应）持有任何长期凭据。
 *
 * <p>账密即全部凭据：通过后一次性下发 token / 登录名 / 有效期。
 *
 * @param token     管理端 JWT（{@code Authorization: Bearer <token>}）
 * @param username  登录名（供端上展示当前身份）
 * @param expiresIn 有效期秒数（默认 86400 = 24h）
 */
@Data
public class AdminLoginVO {

    private String token;

    private String username;

    private long expiresIn;
}