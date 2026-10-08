package com.bjtufood.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员改密请求（{@code POST /admin/auth/password}）。
 *
 * <p>强度校验（≥12 位 + 大写 / 小写 / 数字 / 符号至少三类）在服务端执行 ——
 * 前端提示只是体验，<b>真正的门在服务端</b>。
 *
 * @param oldPassword 当前口令（明文，仅本次请求内存存活）
 * @param newPassword 新口令（明文，仅本次请求内存存活）
 */
@Data
public class PasswordChangeReq {

    @NotBlank(message = "请输入当前密码")
    @Size(max = 128, message = "密码长度超限")
    private String oldPassword;

    @NotBlank(message = "请输入新密码")
    @Size(max = 128, message = "密码长度超限")
    private String newPassword;
}
