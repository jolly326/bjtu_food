package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 处理反馈请求（RB18 收敛：仅 JSON body，不再支持 query 传参）。
 * <p>
 * 契约：{@code PUT /admin/feedbacks/{id}}，请求体 {@code {"reply": "..."}}，reply <b>必填</b>（1~1000 字）。
 * 2026-09-14 用户拍板（project_spec §7.16）：回复内容由选填改为必填——学生收到的处理通知会展示该回复，
 * 空回复等于空通知；纯空白视为未填写，由 {@link NotBlank} 拒绝，校验失败统一返回 400。
 * 处理动作固定为「标记 handled + 写回复 + 记处理时间」，故无需 status 字段；
 * 前端若传多余字段（如历史契约中的 status）由 Jackson 忽略，不影响解析。
 */
@Data
@Schema(description = "处理反馈请求")
public class FeedbackHandleReq {

    /** 管理员回复内容（必填，学生将收到该内容） */
    @Schema(description = "管理员回复内容（必填，学生将收到该内容）", example = "已收到，我们会在下个版本优化", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写处理回复（学生将收到该内容）")
    @Size(max = 1000, message = "回复内容不能超过1000字")
    private String reply;
}
