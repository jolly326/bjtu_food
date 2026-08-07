package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "邮箱验证码重置密码请求参数（邮箱由学号推导，无需填写）")
public class PasswordResetReq {

    @Schema(description = "学号/账号；填此字段时邮箱自动推导为 {学号}@bjtu.edu.cn", example = "20240001")
    private String username;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "校园邮箱（可选）。不传时由 username 推导为 {username}@bjtu.edu.cn", example = "20240001@bjtu.edu.cn")
    private String email;

    @NotBlank(message = "验证码不能为空")
    @Size(min = 4, max = 10, message = "验证码长度应在4-10字符之间")
    @Schema(description = "邮箱验证码（发送至校园邮箱）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "新密码长度应在6-100字符之间")
    @Schema(description = "新登录密码", example = "654321", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}
