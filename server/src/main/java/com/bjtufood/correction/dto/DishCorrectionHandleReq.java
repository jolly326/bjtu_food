package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 拒绝菜品纠错请求（{@code PUT /admin/corrections/{id}}，形态对齐 feedback handle）。
 * <p>
 * 契约：{@code reply} 必填（1~1000 字，纯空白视为未填写 → 400，§7.16 同源口径）；
 * {@code outcome} 固定为 {@code rejected}（本端点即拒绝动作；传其他值 400）；
 * {@code rejectReason} 必填（1~200 字，纯空白 → 400「请填写不采纳原因」，§7.23 第 5 条同源口径）。
 */
@Data
@Schema(description = "拒绝菜品纠错请求")
public class DishCorrectionHandleReq {

    /** 管理员回复内容（必填，提交人将收到该内容） */
    @Schema(description = "管理员回复内容（必填）", example = "经核实价格无误", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写处理回复（提交人将收到该内容）")
    @Size(max = 1000, message = "回复内容不能超过1000字")
    private String reply;

    /** 处理结论：本端点固定 rejected（不采纳/退回） */
    @Schema(description = "处理结论：固定 rejected", example = "rejected")
    private String outcome;

    /** 不采纳原因（必填，1~200 字；纯空白视为未填写 → 400） */
    @Schema(description = "不采纳原因（必填，1~200 字）", example = "该价格与档口今日公示一致", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 200, message = "不采纳原因不能超过200字")
    private String rejectReason;
}
