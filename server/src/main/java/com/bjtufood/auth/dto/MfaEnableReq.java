package com.bjtufood.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * MFA 确认绑定请求（{@code POST /admin/auth/mfa/enable}）。
 *
 * @param secret 由 {@code /admin/auth/mfa/setup} 下发的 Base32 密钥（原样带回）
 * @param code   认证器当前口令（6 位数字）
 */
@Data
public class MfaEnableReq {

    @NotBlank(message = "缺少待绑定密钥")
    @Size(max = 64, message = "密钥长度超限")
    private String secret;

    @NotBlank(message = "请输入认证器动态口令")
    @Size(max = 32, message = "口令长度超限")
    private String code;
}
