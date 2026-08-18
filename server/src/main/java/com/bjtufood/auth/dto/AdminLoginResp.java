package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理后台登录响应（方案 C，spec §5.y.5）
 * <p>
 * 与管理端 web 契约对齐：{ token, username }，role 供前端判断超管权限。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理后台登录响应结果")
public class AdminLoginResp {

    @Schema(description = "JWT Token（有效期7天）")
    private String token;

    @Schema(description = "管理员账号", example = "admin")
    private String username;

    @Schema(description = "角色：admin / super_admin", example = "admin")
    private String role;
}
