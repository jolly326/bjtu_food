package com.bjtufood.apply.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审核退回请求（task-12.1，POST /admin/apply/{id}/reject）
 */
@Data
@Schema(description = "申请退回请求")
public class ApplyHandleReq {

    @Schema(description = "退回原因（rejected 必填）", example = "信息不完整，请补充营业时间")
    @NotBlank(message = "退回原因不能为空")
    private String rejectReason;
}
