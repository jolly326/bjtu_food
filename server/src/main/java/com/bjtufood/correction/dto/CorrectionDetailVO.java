package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 纠错**详情**（{@code GET /admin/corrections/{id}}）：列表字段 + 差异对照 + 提交快照 + 处置结果。
 *
 * <p>列表**不展开各项内容**（那是详情的事）：列表给「改了几项 + 是否涉及楼层」，点进详情看对照。
 * <p>处置结果（状态 / 结论 / 处置文案 / 处置人 / 处置时间）来自处置任务表。
 */
@Data
@Schema(description = "菜品问题反馈详情（含差异对照）")
public class CorrectionDetailVO {

    @Schema(description = "反馈ID")
    private Long id;

    /**
     * 问题类型：{@code field}（信息有误）/ {@code gone}（已经下架）。
     * <p>
     * 管理端<b>据此分派详情内容</b>：field → 展示 {@code differences[]} 提交项对照；gone →
     * <b>不返回 {@code differences}（恒空列表，无可对照项）</b>，只展示 {@code note} + {@code images}
     * （+ {@code goneUserCount}）。两类处置动作相同（标记已处理 / 驳回），<b>均不改写菜品</b>。
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

    @Schema(description = "提交人用户ID（指向 user 表）")
    private Long userId;

    @Schema(description = "提交人昵称（账号已查无时为 null）")
    private String userName;

    @Schema(description = "处置状态：pending / handled")
    private String status;

    @Schema(description = "处置结论：handled=已受理（管理员已手动处理）/ rejected=不采纳；未处置为 null")
    private String outcome;

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
    @Schema(description = "同菜品待处置的 gone 反馈数（已按用户去重；**仅参考，非下架阈值**）", example = "3")
    private Long goneUserCount;

    /**
     * **提交项对照**：只列「用户提交过的项」（见 {@link DishCorrectionDifferenceVO}）。
     * 目标菜品已被物理删除时 {@code oldValue} 为 {@code null}（无从对照）。
     */
    @Schema(description = "提交项对照清单（只列用户提交过的项）")
    private List<DishCorrectionDifferenceVO> differences;

    @Schema(description = "提交的菜品图片 URL 列表（COS 绝对地址）")
    private List<String> images;

    @Schema(description = "处置回执短标题（未处置为 null）")
    private String replyTitle;

    @Schema(description = "处置文案 / 不采纳原因（未处置为 null）")
    private String replyContent;

    @Schema(description = "处置人登录名（未处置 / 账号已删除为 null）")
    private String handledByName;

    @Schema(description = "提交时间")
    private LocalDateTime createdAt;

    @Schema(description = "处置时间（未处置为 null）")
    private LocalDateTime handledAt;
}
