package com.bjtufood.auth.dto;

import lombok.Data;

/**
 * MFA 绑定初始化响应（{@code POST /admin/auth/mfa/setup}）。
 *
 * <p>此时密钥<b>尚未写入账号</b> —— 用户需把它录入认证器，再带着认证器算出的口令调用
 * {@code POST /admin/auth/mfa/enable} 完成绑定。中途放弃则账号状态不变。
 *
 * @param secret     TOTP 密钥（Base32），可手输录入认证器
 * @param otpAuthUri otpauth URI，供认证器扫码
 */
@Data
public class MfaSetupVO {

    private String secret;

    private String otpAuthUri;
}
