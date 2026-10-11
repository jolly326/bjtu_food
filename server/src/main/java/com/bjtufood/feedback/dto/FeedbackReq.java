package com.bjtufood.feedback.dto;

import com.bjtufood.feedback.constant.FeedbackConst;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交意见反馈请求（{@code POST /feedback}）。
 * <p>
 * 反馈类型以**字典项 ID**（{@link #optionId}）上送，值域 = {@code GET /feedback-options} 下发的启用项
 * —— 端上零硬编码，新增类型无需发版。
 * <p>
 * <b>与举报 / 纠错的边界</b>：评价举报走 {@code POST /reviews/{id}/report}、菜品信息纠错走
 * {@code POST /dishes/{id}/correction}，故本请求体只承载纯反馈的内容与配图。
 */
@Data
@Schema(description = "提交意见反馈请求")
public class FeedbackReq {

    @Schema(description = "反馈类型ID（值域 = GET /feedback-options 下发项的 id）", example = "1")
    @NotNull(message = "请选择反馈类型")
    private Long optionId;

    @Schema(description = "反馈内容（必填，≤1000 字）", example = "希望增加更多素食档口")
    @NotBlank(message = "反馈内容不能为空")
    @Size(max = FeedbackConst.CONTENT_MAX_LENGTH, message = "反馈内容不能超过1000字")
    private String content;

    @Schema(description = "反馈配图 URL 列表（经 POST /upload/cloud-image 转存的 COS 绝对地址，≤3 张）")
    @Size(max = FeedbackConst.IMAGE_MAX, message = "反馈配图最多 3 张")
    private List<String> images;
}
