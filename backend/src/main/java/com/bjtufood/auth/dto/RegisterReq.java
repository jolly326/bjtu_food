package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求 DTO
 * <p>
 * 接收前端传入的注册信息，POST /api/auth/register 的请求体
 */
@Data
@Schema(description = "注册请求参数")
public class RegisterReq {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度应在3-50字符之间")
    @Schema(description = "学号/工号", example = "stu003", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度应在6-100字符之间")
    @Schema(description = "密码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Schema(description = "昵称", example = "王五", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;
}
