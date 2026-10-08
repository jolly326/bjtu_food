package com.bjtufood.auth.dto;

import lombok.Data;

/**
 * 管理端登录响应（{@code POST /admin/auth/login} 与 {@code POST /admin/auth/login/mfa} 共用）。
 *
 * <p>🔴 <b>只回 token，不回口令、不回哈希</b>：前端凭此 token 维持登录态，
 * 无需（也不应）持有任何长期凭据。
 *
 * <p><b>两种形态</b>：
 * <ul>
 *   <li>{@code mfaRequired = false}：账密即全部凭据 ⇒ 下发 {@code token / username / expiresIn}；</li>
 *   <li>{@code mfaRequired = true}：账号已绑定 MFA ⇒ 本步<b>不签发 token</b>，
 *       只下发短时票据 {@code mfaTicket}，端上据此调用 {@code /admin/auth/login/mfa} 完成第二步。</li>
 * </ul>
 *
 * @param token       管理端 JWT（{@code Authorization: Bearer <token>}）；需要第二因子时为 {@code null}
 * @param username    登录名（供端上展示当前身份）；需要第二因子时为 {@code null}
 * @param expiresIn   有效期秒数（默认 86400 = 24h）；需要第二因子时为 0
 * @param mfaRequired 是否还需完成第二因子
 * @param mfaTicket   第二因子票据（仅 {@code mfaRequired = true} 时下发；短时、一次性、不可当 token 用）
 */
@Data
public class AdminLoginVO {

    private String token;
    private String username;
    private long expiresIn;
    private boolean mfaRequired;
    private String mfaTicket;
}