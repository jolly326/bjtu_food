package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理后台登录请求（方案 C，spec §5.y.5）
 * <p>
 * 管理员账号密码 + BCrypt + JWT，与小程序微信登录体系解耦。
 * 仅校验 role ∈ {admin, super_admin}。
 */
@Data
@Schema(description = "管理后台登录请求参数（方案 C：账号密码）")
public class AdminLoginReq {

    @NotBlank(message = "账号不能为空")
    @Schema(description = "管理员账号（username）", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    private String account;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "admin123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
