package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 学号邮箱认证请求（spec §5.y.5 / task-01 1.3）
 * <p>
 * 入参仅验证码 code；绑定邮箱由验证码对应记录推导，当前微信账号从请求上下文（JWT userId）取。
 * 认证通过后：无历史邮箱则直接绑定+verified=1；有历史邮箱则数据归属转移；已被他微信绑定则替换绑定。
 */
@Data
@Schema(description = "学号邮箱认证请求参数")
public class VerifyEmailReq {

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "邮箱验证码（经 /auth/email-code purpose=verify 发送至校园邮箱）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}
