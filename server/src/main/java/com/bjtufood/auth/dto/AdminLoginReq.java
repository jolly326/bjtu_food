package com.bjtufood.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端账密登录请求（{@code POST /admin/auth/login}）。
 *
 * <p>🔴 {@code password} 是**明文口令**，仅在本次请求的生命周期内存在于内存与请求体中：
 * 不落库（服务端只存 BCrypt 哈希）、不写日志、不返回。
 */
@Data
public class AdminLoginReq {

    /** 管理员登录名（{@code admin_account.username}） */
    @NotBlank(message = "请输入账号")
    @Size(max = 64, message = "账号长度超限")
    private String username;

    /** 明文口令（服务端以 BCrypt 比对） */
    @NotBlank(message = "请输入密码")
    @Size(max = 128, message = "密码长度超限")
    private String password;
}