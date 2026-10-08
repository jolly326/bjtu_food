package com.bjtufood.auth.dto;

import lombok.Data;

import java.util.List;

/**
 * MFA 绑定成功响应（{@code POST /admin/auth/mfa/enable}）。
 *
 * <p>🔴 {@code recoveryCodes} <b>只在此刻下发一次</b>：服务端仅保留其哈希，
 * 关闭本页即无处再取 —— 用户须当场抄录离线保存。
 *
 * @param recoveryCodes 一次性恢复码（设备丢失时的登录兜底，用一枚作废一枚）
 */
@Data
public class MfaEnableVO {

    private List<String> recoveryCodes;
}
