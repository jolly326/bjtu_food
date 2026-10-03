package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 档口确认出参（两段式档口确认的第一段响应，HTTP 200）。
 * <p>
 * {@code needStallConfirm=true} 表示本次调用<b>未执行采纳</b>（提交的档口名未命中现有档口）：
 * {@code candidates} 为候选档口（提交食堂名匹配现有食堂时 = 该食堂下全部档口；
 * 无匹配食堂时 = 全量档口），供管理端选择既有档口（带 stallId 二次调用）
 * 或确认新建（createIfMissing=true 二次调用）。
 */
public record StallConfirmVO(
        @Schema(description = "是否需要档口确认（true = 本次未执行采纳）", example = "true")
        boolean needStallConfirm,
        @Schema(description = "候选档口列表 [{id, name}]")
        List<StallCandidate> candidates) {

    /**
     * 候选档口单行（id + name，与管理端档口字典同构）。
     */
    @Schema(description = "候选档口")
    public record StallCandidate(
            @Schema(description = "档口ID", example = "3")
            Long id,
            @Schema(description = "档口名称", example = "学一基本伙食")
            String name) {
    }
}
