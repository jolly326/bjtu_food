package com.bjtufood.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * MFA 登录第二步请求（{@code POST /admin/auth/login/mfa}）。
 *
 * <p>{@code mfaTicket} 是账密校验通过后签发的**短时一次性票据**（5 分钟），
 * 它只证明「第一步已通过」，<b>不能当 token 用</b>（{@code AdminAuthFilter} 对其 fail-closed）。
 *
 * @param mfaTicket 第一步返回的票据
 * @param code      认证器动态口令或一枚未使用的恢复码
 */
@Data
public class MfaLoginReq {

    @NotBlank(message = "缺少登录票据")
    @Size(max = 2048, message = "登录票据长度超限")
    private String mfaTicket;

    @NotBlank(message = "请输入动态口令")
    @Size(max = 32, message = "口令长度超限")
    private String code;
}
