package com.bjtufood.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 审核退回请求
 * <p>
 * rejectReason 必填（见 spec §3.x.1 / task-09）；批量退回时附带 ids。
 */
@Data
@Schema(description = "审核退回请求")
public class AuditRejectReq {

    @NotBlank(message = "退回原因不能为空")
    @Schema(description = "退回原因", example = "图片不清晰，请重新上传")
    private String rejectReason;

    @Schema(description = "批量退回时的记录ID列表")
    private List<@NotNull Long> ids;
}
