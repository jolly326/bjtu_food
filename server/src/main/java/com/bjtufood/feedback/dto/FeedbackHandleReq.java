package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 处理反馈请求（RB18 收敛：仅 JSON body，不再支持 query 传参）。
 * <p>
 * 契约：{@code PUT /admin/feedbacks/{id}}，请求体 {@code {"reply": "..."}}，reply 可选（≤1000 字）。
 * 处理动作固定为「标记 handled + 写回复 + 记处理时间」，故无需 status 字段；
 * 前端若传多余字段（如历史契约中的 status）由 Jackson 忽略，不影响解析。
 */
@Data
@Schema(description = "处理反馈请求")
public class FeedbackHandleReq {

    /** 管理员回复内容（选填） */
    @Schema(description = "管理员回复内容（选填）", example = "已收到，我们会在下个版本优化")
    @Size(max = 1000, message = "回复内容不能超过1000字")
    private String reply;
}
