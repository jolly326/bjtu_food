package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A7 举报原因改名请求（**只改 `name`**，改名免费）。
 */
@Data
@Schema(description = "举报原因改名请求")
public class ReportReasonRenameReq {

    @Schema(description = "中文名（1~32 字）", example = "垃圾广告 / 营销刷屏")
    @NotBlank(message = "中文名不能为空")
    @Size(max = 32, message = "中文名不能超过 32 字")
    private String name;
}
