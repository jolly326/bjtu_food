package com.bjtufood.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * MFA 停用请求（{@code POST /admin/auth/mfa/disable}）。
 *
 * <p>🔴 <b>必须同时提供口令与动态口令</b>：停用 MFA 是「降低账号防护」的动作，
 * 若只认 token，则 token 被盗用的一方可顺手把第二因子摘掉，把「泄露口令也进不去」
 * 的账号重新变成「拿到 token 就全权」。口令证明是本人，动态口令证明第二因子在手。
 *
 * @param password 当前登录口令（明文，仅本次请求内存存活）
 * @param code     认证器动态口令或一枚未使用的恢复码
 */
@Data
public class MfaDisableReq {

    @NotBlank(message = "请输入当前密码")
    @Size(max = 128, message = "密码长度超限")
    private String password;

    @NotBlank(message = "请输入动态口令")
    @Size(max = 32, message = "口令长度超限")
    private String code;
}
