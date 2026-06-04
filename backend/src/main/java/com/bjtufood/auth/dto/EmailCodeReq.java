package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "邮箱验证码请求参数")
public class EmailCodeReq {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "校园邮箱", example = "20240001@bjtu.edu.cn", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "验证码用途：login=登录，register=注册。为空时默认为 login", example = "login")
    private String purpose;
}
