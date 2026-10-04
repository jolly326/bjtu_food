package com.bjtufood.banner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * A5 Banner 启停请求（**只改 `status`**，显式传目标状态、非 toggle）。
 */
@Data
@Schema(description = "Banner 启停请求")
public class BannerStatusReq {

    @Schema(description = "状态：on=启用 / off=停用", example = "off")
    @NotBlank(message = "status 不能为空")
    private String status;
}
