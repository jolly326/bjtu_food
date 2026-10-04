package com.bjtufood.feedback.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报原因字典实体（A7 落地，2026-10-03）。
 * <p>
 * 对应表 `report_reason` —— 举报弹层「原因」单选的**可维护字典**（原为代码常量 `FeedbackConst.REPORT_REASONS`）。
 * <ul>
 *   <li>{@code value}：机器值，**数据锚点**（历史举报按它落库到 `user_feedback.sub`）⇒ **在用后不可改**；</li>
 *   <li>{@code label}：中文标签，**改名免费**（历史举报的中文翻译随表实时生效）；</li>
 *   <li>{@code status}：`on` / `off` —— 公开端点只下发启用项，提交白名单同样只认启用项；</li>
 *   <li>删除受引用约束：被任一举报引用即 `400`（下线一律用停用）。</li>
 * </ul>
 * 口径真源：docs/schema/report_reason.md 与 docs/api/web/report-reasons.md。
 */
@Data
@TableName("report_reason")
@Schema(description = "举报原因（dict）")
public class ReportReason {

    @TableId(type = IdType.AUTO)
    @Schema(description = "原因ID")
    private Long id;

    /** 机器值（小写字母 / 数字 / `-`；举报记录按它落库；**在用后不可改**） */
    @Schema(description = "机器值（在用后不可改）", example = "spam")
    private String value;

    /** 中文标签（可改，改名免费） */
    @Schema(description = "中文标签", example = "垃圾广告 / 营销刷屏")
    private String label;

    /** 展示顺序（升序） */
    @Schema(description = "展示顺序（升序）")
    private Integer order;

    /** 状态：on=启用 / off=停用 */
    @Schema(description = "状态：on=启用 / off=停用")
    private String status;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
