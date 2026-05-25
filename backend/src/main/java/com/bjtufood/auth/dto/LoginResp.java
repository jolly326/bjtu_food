package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应 DTO
 * <p>
 * 登录成功后返回给前端的信息，包含 JWT Token 和用户基本信息
 */
@Data
@AllArgsConstructor
@Schema(description = "登录响应结果")
public class LoginResp {

    @Schema(description = "JWT Token（有效期7天）", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "角色", example = "student")
    private String role;

    @Schema(description = "绑定的档口ID（仅食堂管理员有值）")
    private Long stallId;
}
