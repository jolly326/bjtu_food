package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A7 新增举报原因请求。
 *
 * <p>只填中文 `label`；原因 ID 由后端生成、是历史举报的数据锚点（`user_feedback.sub_reason_id`），
 * 故**没有**「改 ID」的端点。
 */
@Data
@Schema(description = "举报原因新增请求")
public class ReportReasonSaveReq {

    @Schema(description = "中文标签（1~32 字）", example = "垃圾广告 / 营销刷屏")
    @NotBlank(message = "中文标签不能为空")
    @Size(max = 32, message = "中文标签不能超过 32 字")
    private String label;
}
