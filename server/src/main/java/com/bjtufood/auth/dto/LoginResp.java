package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应 DTO（微信登录体系，spec §5.y.5）
 * <p>
 * 由 {@code POST /auth/wechat-login} 与 {@code POST /auth/verify-email} 返回：
 * 结构为 {@code { token, userInfo }}，其中 userInfo 为小程序端账号信息（含 verified / bindEmail / guestShortId）。
 * JWT 7 天；verified 不进 JWT，后端按 user.verified 实时判定。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应结果（token + 用户信息）")
public class LoginResp {

    @Schema(description = "JWT Token（有效期7天）", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "用户信息（含 verified/bindEmail/guestShortId）")
    private UserInfoVO userInfo;
}
