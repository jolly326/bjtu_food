package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 处理反馈请求（RB18 收敛：仅 JSON body，不再支持 query 传参）。
 * <p>
 * 契约：{@code PUT /admin/feedbacks/{id}}（B2 意见反馈与 B3 举报管理**共用**）。
 * <ul>
 *   <li>{@code reply} <b>可选</b>（≤1000 字；B2 2026-10-03 由必填改为可选）——
 *       处理回复没有信息增量时不该强制填写，空值由回执的固定文案兜底；
 *       <b>仅 {@code outcome=rejected} 时 {@code rejectReason} 必填</b>。</li>
 *   <li>{@code outcome} 处理结论（§7.23 第 5 条）：{@code handled}=通过/已处理（缺省）；
 *       {@code rejected}=不采纳/退回。非法值由 Service 层 400 拦截。</li>
 *   <li>{@code rejectReason} 不采纳原因：outcome=rejected 时<b>必填</b>（1~200 字，纯空白 → 400
 *       「请填写不采纳原因」）；outcome=handled 时不消费（保持落库 NULL）。</li>
 *   <li>{@code hideReview} <b>B3 专用</b>：处置举报时**同时隐藏**被举报评价
 *       （默认勾选；评价已隐藏时端上置灰）。仅对 {@code type=report} 且存在被举报评价时生效。</li>
 * </ul>
 * 处理动作固定为「标记 handled + 写回复/结论 + 记处理时间（+ 举报联动隐藏）」；
 * 前端若传多余字段由 Jackson 忽略，不影响解析。
 */
@Data
@Schema(description = "处理反馈请求")
public class FeedbackHandleReq {

    /** 管理员回复内容（**可选**；留空时回执用固定结论文案） */
    @Schema(description = "管理员回复内容（可选，≤1000 字）", example = "已收到，我们会在下个版本优化")
    @Size(max = 1000, message = "回复内容不能超过1000字")
    private String reply;

    /** 处理结论：handled=通过/已处理（缺省）；rejected=不采纳/退回 */
    @Schema(description = "处理结论：handled=通过/已处理（缺省）；rejected=不采纳/退回", example = "handled")
    private String outcome;

    /** 不采纳原因（outcome=rejected 时必填，1~200 字；纯空白视为未填写 → 400） */
    @Schema(description = "不采纳原因（outcome=rejected 时必填，1~200 字）", example = "该问题已在近期版本修复")
    @Size(max = 200, message = "不采纳原因不能超过200字")
    private String rejectReason;

    /** B3 专用：处置举报时**同时隐藏**被举报评价（评价已隐藏时端上置灰） */
    @Schema(description = "B3 专用：是否同时隐藏被举报评价（默认勾选；评价已隐藏时置灰）", example = "true")
    private Boolean hideReview;
}
