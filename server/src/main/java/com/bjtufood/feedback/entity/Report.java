package com.bjtufood.feedback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价举报实体（用户提交的**事实**，append-only）。
 * <p>
 * 对应数据库表：`report` —— 只存提交信息，**无状态列、无正文列**：
 * 结论以结构化原因（{@link #optionId}）为准，「是否已处置 + 处置文案」外置在
 * {@code moderation_task}（多态键 {@code (type='report', type_id)}）。
 * <ul>
 *   <li>{@link #reviewId}：被举报评价 ID，**可为 NULL**（外键 {@code fk_report_review} →
 *       {@code review(id)} 的 {@code ON DELETE SET NULL}）：评价被删 ⇒ 本行**保留**、
 *       该列置 NULL = <b>显式</b>表达「被举报评价已删除」，举报人 / 原因 / 处置状态全部完整；</li>
 *   <li>{@link #optionId}：举报原因（`report_option` 字典，提交时校验命中启用项）。</li>
 * </ul>
 * 口径真源：docs/schema/report.md。
 */
@Data
@TableName("report")
@Schema(description = "评价举报")
public class Report {

    @TableId(type = IdType.AUTO)
    @Schema(description = "举报ID")
    private Long id;

    /** 举报人用户ID（外键 {@code fk_report_user} → {@code user(id)}，恒为真实登录用户） */
    @Schema(description = "举报人用户ID")
    private Long userId;

    /** 被举报评价 ID（评价被删除时置 NULL —— 显式表达「被举报评价已删除」，行本身保留） */
    @Schema(description = "被举报评价ID（被举报评价已删除为 null）")
    private Long reviewId;

    /** 举报原因（`report_option` 字典项 ID；提交时校验该字典项存在且启用） */
    @Schema(description = "举报原因（report_option 字典项 ID）")
    private Long optionId;

    /** 创建时间（DB 时钟：INSERT 由 `DEFAULT CURRENT_TIMESTAMP` 写入，应用层不写、不填充） */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
