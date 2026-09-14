package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 处理反馈请求（RB18 收敛：仅 JSON body，不再支持 query 传参）。
 * <p>
 * 契约：{@code PUT /admin/feedbacks/{id}}。
 * <ul>
 *   <li>{@code reply} <b>必填</b>（1~1000 字）：学生收到的处理通知会展示该回复，
 *       空回复等于空通知；纯空白视为未填写，由 {@link NotBlank} 拒绝，校验失败统一返回 400（§7.16）。</li>
 *   <li>{@code outcome} 处理结论（§7.23 第 5 条）：{@code handled}=通过/已处理（缺省）；
 *       {@code rejected}=不采纳/退回。非法值由 Service 层 400 拦截。</li>
 *   <li>{@code rejectReason} 不采纳原因：outcome=rejected 时<b>必填</b>（1~200 字，纯空白视为未填写 →
 *       400「请填写不采纳原因」）；outcome=handled 时不消费（保持落库 NULL）。</li>
 * </ul>
 * 处理动作固定为「标记 handled + 写回复/结论 + 记处理时间」；前端若传多余字段由 Jackson 忽略，不影响解析。
 */
@Data
@Schema(description = "处理反馈请求")
public class FeedbackHandleReq {

    /** 管理员回复内容（必填，学生将收到该内容） */
    @Schema(description = "管理员回复内容（必填，学生将收到该内容）", example = "已收到，我们会在下个版本优化", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写处理回复（学生将收到该内容）")
    @Size(max = 1000, message = "回复内容不能超过1000字")
    private String reply;

    /** 处理结论：handled=通过/已处理（缺省）；rejected=不采纳/退回 */
    @Schema(description = "处理结论：handled=通过/已处理（缺省）；rejected=不采纳/退回", example = "handled")
    private String outcome;

    /** 不采纳原因（outcome=rejected 时必填，1~200 字；纯空白视为未填写 → 400） */
    @Schema(description = "不采纳原因（outcome=rejected 时必填，1~200 字）", example = "该问题已在近期版本修复")
    @Size(max = 200, message = "不采纳原因不能超过200字")
    private String rejectReason;
}
