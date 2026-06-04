package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
@Schema(description = "登录请求参数")
public class LoginReq {

    @Schema(description = "密码登录账号，可填写用户名/学号/邮箱", example = "20240001")
    private String account;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "验证码登录邮箱", example = "20240001@bjtu.edu.cn")
    private String email;

    @Schema(description = "登录密码。传 password 时走密码登录", example = "123456")
    private String password;

    @Schema(description = "邮箱验证码。传 code 时走验证码登录", example = "123456")
    private String code;
}
