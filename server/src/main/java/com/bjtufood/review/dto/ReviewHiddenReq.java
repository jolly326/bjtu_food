package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设置评价可见性（{@code PUT /admin/reviews/{id}/hidden}）的请求体。
 *
 * <p><b>为何 {@code hidden} 必填</b>：端点是「设置状态」而非「切换（toggle）」——
 * 缺省即静默按 {@code false} 处理会把一次<b>漏传参数的隐藏请求</b>变成<b>恢复显示</b>，
 * 方向与后果都不可接受（见 {@code docs/api/web/reviews.md}）。
 *
 * <p>{@code note} 只在隐藏时落库：恢复显示时服务端置 {@code NULL}（避免旧附注残留到下一条回执）。
 */
@Data
@Schema(description = "设置评价隐藏状态的请求")
public class ReviewHiddenReq {

    @Schema(description = "是否隐藏：true 隐藏 / false 恢复显示（必填）", example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "hidden 不能为空")
    private Boolean hidden;

    @Schema(description = "隐藏附注（可选，≤200 字；隐藏时随回执下发给作者，恢复显示时忽略）",
            example = "该评价含与菜品无关的辱骂内容")
    @Size(max = 200, message = "隐藏附注不能超过 200 字")
    private String note;
}
