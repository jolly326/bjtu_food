package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO
 * <p>
 * 接收前端传入的账号密码，POST /api/auth/login 的请求体
 */
@Data
@Schema(description = "登录请求参数")
public class LoginReq {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "学号/工号", example = "stu001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
