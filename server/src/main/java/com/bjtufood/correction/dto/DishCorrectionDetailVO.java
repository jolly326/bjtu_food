package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 纠错**详情**（{@code GET /admin/corrections/{id}}）：列表字段 + 差异对照 + 提交快照。
 *
 * <p>契约真源：docs/api/web/corrections.md 的「响应 · `DishCorrectionDetailVO`」。
 * 列表**不展开各项内容**（那是详情的事）：列表给「改了几项 + 是否涉及楼层」，点进详情看对照。
 */
@Data
@Schema(description = "菜品问题反馈详情（含差异对照）")
public class DishCorrectionDetailVO {

    @Schema(description = "反馈ID")
    private Long id;

    /**
     * 问题类型：{@code field}（信息有误）/ {@code gone}（已经下架）。
     * <p>
     * 管理端<b>据此分派处置</b>：field → 展示 {@code differences[]} 逐项采纳；gone → <b>不返回
     * {@code differences}（恒空列表，无可对照差异项），只展示 {@code note} + {@code images}，
     * 且处置动作<b>仅「下架」</b>（🔴 绝不提供「删除」）。
     */
    @Schema(description = "问题类型：field=信息有误 / gone=已经下架", example = "field")
    private String type;

    @Schema(description = "目标菜品ID")
    private Long dishId;

    @Schema(description = "目标菜品名（实时回查 dish；菜品已物理删除为 null）")
    private String dishName;

    /**
     * 目标菜品**当前**上下架状态（{@code on} / {@code off}），实时回查。
     * <p>
     * 供管理端在处置前判断菜品是否已在售 / 已下架；菜品已物理删除时为 null。
     */
    @Schema(description = "目标菜品当前上下架状态（on/off；菜品已物理删除为 null）", example = "on")
    private String dishStatus;

    @Schema(description = "提交人用户ID（匿名提交为 0）")
    private Long userId;

    @Schema(description = "提交人昵称（匿名提交为 null）")
    private String userNickname;

    @Schema(description = "处理状态：pending/adopted/rejected")
    private String status;

    /**
     * 补充说明（<b>仅 {@code type=gone} 的选填补充</b>，≤200 字；field 型恒 null）。
     * <p>
     * gone 型的<b>核心判读依据</b>：「变成了别的菜」/「换窗口了」/「今天临时没供」
     * 三种情况的处置动作完全不同（补录新菜 / 改档口 / 不下架）—— 只看「N 人反馈已下架」会误判。
     */
    @Schema(description = "补充说明（≤200 字；仅 type=gone 的选填补充，field 型为 null）",
            example = "这个窗口现在换成麻辣香锅了")
    private String note;

    /**
     * 「N 人反馈已下架」（<b>仅 {@code type=gone} 下发</b>，同用户对同一菜品已去重）。
     * <p>
     * ⚠️ <b>仅作参考，不是下架阈值</b> —— ≥1 条即进待办，是否下架由管理员人工决定。
     */
    @Schema(description = "同菜品待处理的 gone 反馈数（已按用户去重；**仅参考，非下架阈值**）", example = "3")
    private Long goneUserCount;

    /**
     * **采纳清单**：仅「仍有差异」的项（见 {@link DishCorrectionDifferenceVO}）。
     * 目标菜品已被物理删除时为空列表（无从对照；采纳本身也会 4001）。
     */
    @Schema(description = "差异对照清单（仅仍有差异的项）")
    private List<DishCorrectionDifferenceVO> differences;

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
