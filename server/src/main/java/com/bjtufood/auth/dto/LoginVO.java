package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应 VO（微信登录体系，spec §5.y.5）
 * <p>
 * 仅由 {@code POST /auth/wechat-login} 返回：结构为 {@code { token, userInfo }}，
 * 其中 userInfo 为小程序端账号信息（{@link UserInfoVO}，恰 6 字段）。
 * JWT 7 天；认证态不进 JWT（无 verified 字段），后端按 {@code bind_email} 非空实时判定。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应结果（token + 用户信息）")
public class LoginVO {

    @Schema(description = "JWT Token（有效期7天）", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "用户信息（恰 6 字段：id/username/nickname/avatar/bindEmail/createdAt）")
    private UserInfoVO userInfo;
}
