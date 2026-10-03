package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 纠错**详情**（{@code GET /admin/corrections/{id}}）：列表字段 + 差异对照 + 提交快照。
 *
 * <p>契约真源：docs/web/B-UGC治理/B4-菜品纠错管理.md 的「响应 · `DishCorrectionDetailVO`」。
 * 列表**不展开各项内容**（那是详情的事）：列表给「改了几项 + 是否涉及楼层」，点进详情看对照。
 */
@Data
@Schema(description = "菜品纠错详情（含差异对照）")
public class DishCorrectionDetailVO {

    @Schema(description = "纠错ID")
    private Long id;

    @Schema(description = "目标菜品ID")
    private Long dishId;

    @Schema(description = "目标菜品名（实时回查 dish；菜品已物理删除为 null）")
    private String dishName;

    @Schema(description = "提交人用户ID（匿名提交为 null）")
    private Long userId;

    @Schema(description = "提交人昵称（匿名提交为 null）")
    private String userNickname;

    @Schema(description = "处理状态：pending/adopted/rejected")
    private String status;

    /**
     * **采纳清单**：仅「仍有差异」的项（见 {@link DishCorrectionDifferenceVO}）。
     * 目标菜品已被物理删除时为空列表（无从对照；采纳本身也会 4001）。
     */
    @Schema(description = "差异对照清单（仅仍有差异的项）")
    private List<DishCorrectionDifferenceVO> differences;

    /** 用户提交的原始快照（仅改动项）—— 供「已同步 / 已被改回」等场景回看。 */
    @Schema(description = "用户提交的原始快照（仅改动项：name/price/canteenName/stallName/floor/attributes/images）")
    private Map<String, Object> submitted;

    @Schema(description = "提交的菜品图片 URL 列表（COS 绝对地址）")
    private List<String> images;

    @Schema(description = "处理回复")
    private String reply;

    @Schema(description = "不采纳原因（status=rejected 时非空）")
    private String rejectReason;

    @Schema(description = "处理时间")
    private LocalDateTime handledAt;

    @Schema(description = "提交时间")
    private LocalDateTime createdAt;
}
