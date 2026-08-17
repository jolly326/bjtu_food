package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评价回复请求参数（楼中楼）
 */
@Data
@Schema(description = "评价回复请求参数")
public class ReviewReplyReq {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容不能超过500字")
    @Schema(description = "回复内容", example = "同意，我也觉得不错！", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
}
