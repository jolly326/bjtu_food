package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * C2 用户启用 / 禁用请求（**只改 `status`**）。
 *
 * <p>启停类端点全站统一用 <b>字符串枚举</b>形态（同 {@code ReportReasonStatusReq} 的 on/off），
 * 不采用 {@code {"disabled": true}} 之类布尔形态。
 *
 * <p>取值域由服务端强制（{@code UserServiceImpl#updateStatus}）：**仅** {@code active} / {@code disabled}；
 * 缺失 / 空白 → {@code 400}「状态不能为空」（{@code @NotBlank}），传 {@code deleted} 或其它值
 * → {@code 400}「非法的状态：xxx」。
 */
@Data
@Schema(description = "用户启用/禁用请求")
public class UserStatusReq {

    @Schema(description = "状态：active=启用 / disabled=禁用", example = "disabled")
    @NotBlank(message = "状态不能为空")
    private String status;
}
