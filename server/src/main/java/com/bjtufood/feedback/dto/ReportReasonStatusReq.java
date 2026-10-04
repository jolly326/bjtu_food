package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * A7 举报原因启停请求（**只改 `status`**）。
 *
 * <p>两条不变量由服务端强制：**停用最后一条启用 → `400`**（举报入口不能配空）、
 * **启用数已达上限 8 → `400`**（单选弹层可用性约束）。
 */
@Data
@Schema(description = "举报原因启停请求")
public class ReportReasonStatusReq {

    @Schema(description = "状态：on=启用 / off=停用", example = "off")
    @NotBlank(message = "status 不能为空")
    private String status;
}
