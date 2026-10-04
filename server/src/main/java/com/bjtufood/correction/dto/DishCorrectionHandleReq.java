package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 拒绝菜品问题反馈请求（{@code PUT /admin/corrections/{id}}，形态对齐 feedback handle）。
 * <p>
 * {@code reply} <b>可选</b>（与 {@code api/web/feedback.md} 的处理回复同口径）：不采纳时管理员真正要填的
 * 是 {@code rejectReason}，强制多填一份回复没有信息增量。
 * <ul>
 *   <li>{@code outcome} 固定为 {@code rejected}（本端点即拒绝动作；传其他值 400）；</li>
 *   <li>{@code rejectReason} <b>必填</b>（1~200 字，纯空白 → 400「请填写不采纳原因」）；</li>
 *   <li>{@code reply} 可选（≤600 字，与 {@code CorrectionConst.REPLY_MAX_LENGTH} 同源）；
 *       留空时回执正文以「不采纳原因」呈现。</li>
 * </ul>
 */
@Data
@Schema(description = "拒绝菜品问题反馈请求")
public class DishCorrectionHandleReq {

    /** 管理员回复内容（**可选**；提交人将收到该内容；留空时回执以不采纳原因为正文） */
    @Schema(description = "管理员回复内容（可选，≤600 字）", example = "经核实价格无误")
    @Size(max = 600, message = "回复内容不能超过600字")
    private String reply;

    /** 处理结论：本端点固定 rejected（不采纳/退回） */
    @Schema(description = "处理结论：固定 rejected", example = "rejected")
    private String outcome;

    /** 不采纳原因（必填，1~200 字；纯空白视为未填写 → 400） */
    @Schema(description = "不采纳原因（必填，1~200 字）", example = "该价格与档口今日公示一致",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写不采纳原因")
    @Size(max = 200, message = "不采纳原因不能超过200字")
    private String rejectReason;
}
