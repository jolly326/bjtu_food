package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交评价举报请求（{@code POST /reviews/{id}/report}，被举报评价 ID 在路径上）。
 * <p>
 * 举报结论以**结构化原因**为准（{@link #optionId} 必选，值域 = {@code GET /report-options} 的启用项）。
 * 举报表只存提交事实（举报人 / 被举报评价 / 原因 / 时间），**无补充说明与配图列**
 * —— 需要补充说明的场景走意见反馈。
 */
@Data
@Schema(description = "提交评价举报请求")
public class ReportReq {

    @Schema(description = "举报原因ID（必选，值域 = GET /report-options 下发项的 id）", example = "1")
    @NotNull(message = "举报原因不能为空")
    private Long optionId;
}
