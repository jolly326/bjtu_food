package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交评价举报请求（{@code POST /reviews/{id}/report}，被举报评价 ID 在路径上）。
 * <p>
 * 举报结论以<b>结构化原因</b>为准（{@code reasonId} 必选），文本（{@code content}）仅作补充说明、<b>可空</b>。
 * 落库为 {@code user_feedback} 的 {@code type='report'} 记录：
 * {@code sub_reason_id = reasonId}、{@code related_type='review'}、{@code related_id = 路径 id}。
 * <p>
 * 请求体<b>无</b> {@code type} / {@code relatedType} / {@code relatedId}——被举报对象由路径表达。
 */
@Data
@Schema(description = "提交评价举报请求")
public class ReportReq {

    @Schema(description = "举报原因 ID（必选，值域 = GET /report-reasons 下发项的 id）", example = "1")
    @NotNull(message = "举报原因不能为空")
    private Long reasonId;

    @Schema(description = "补充说明（可空，≤1000 字）", example = "疑似广告刷屏")
    @Size(max = 1000, message = "补充说明不能超过1000字")
    private String content;

    @Size(max = 3, message = "举报配图最多 3 张")
    @Schema(description = "佐证配图 URL 列表（经 POST /upload/cloud-image 转存，≤3 张）")
    private List<String> images;
}
