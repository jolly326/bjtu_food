package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交意见反馈请求（{@code POST /feedback}）。
 * <p>
 * 仅承载<b>纯反馈</b>（{@code type} ∈ {@code bug} / {@code suggestion} / {@code other}）：
 * 具体描述 {@code content} 必填 + 截图 {@code images}（≤3 张）。
 * <p>
 * <b>与举报 / 纠错的边界（2026-09-29 方案 B 拆分）</b>：评价举报走独立端点
 * {@code POST /reviews/{id}/report}（{@link ReportReq}）；菜品信息纠错走
 * {@code POST /dishes/{id}/correction}。故本请求体<b>不含</b> {@code sub} /
 * {@code relatedType} / {@code relatedId}（举报的关联对象已改由路径表达）。
 */
@Data
@Schema(description = "提交意见反馈请求")
public class FeedbackReq {

    /**
     * 反馈类型写入值域（单一真源 {@code FeedbackConst.WRITABLE_TYPES}）：
     * {@code bug}（程序功能 Bug）/ {@code suggestion}（产品功能建议）/ {@code other}（其他平台相关问题）；
     * 非法 / 历史遗留类型（{@code issue} / {@code add} / {@code error} / {@code report}）→ 400。
     */
    @Schema(description = "反馈类型：bug=程序功能Bug / suggestion=产品功能建议 / other=其他相关问题", example = "bug")
    @NotBlank(message = "反馈类型不能为空")
    private String type;

    @Schema(description = "反馈内容（必填，≤1000 字）", example = "希望增加更多素食档口")
    @Size(max = 1000, message = "反馈内容不能超过1000字")
    private String content;

    @Size(max = 3, message = "反馈配图最多 3 张")
    @Schema(description = "反馈配图 URL 列表（经 POST /upload/cloud-image 转存的 COS 绝对地址，≤3 张）")
    private List<String> images;
}
