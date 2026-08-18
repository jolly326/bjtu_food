package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信静默登录请求（spec §5.y.5）
 * <p>
 * 入参为微信 {@code wx.login} 获取的临时登录凭证 code，后端经 code2Session 换 openid。
 */
@Data
@Schema(description = "微信静默登录请求参数")
public class WechatLoginReq {

    @NotBlank(message = "code 不能为空")
    @Schema(description = "微信 wx.login 临时凭证", example = "0a3b...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}
