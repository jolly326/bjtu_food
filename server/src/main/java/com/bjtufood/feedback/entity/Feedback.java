package com.bjtufood.feedback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 意见反馈实体（用户提交的**事实**，append-only）。
 * <p>
 * 对应数据库表：`feedback` —— 只存提交信息，**无状态列**：
 * 「是否已处置 + 处置文案」外置在 {@code moderation_task}（多态键 {@code (type='feedback', type_id)}）。
 * <ul>
 *   <li>{@link #optionId}：反馈类型（`feedback_option` 字典，提交时校验命中启用项）；</li>
 *   <li>{@link #content}：具体描述（必填，≤1000 字，过本地词库 + 微信内容安全检测）；</li>
 *   <li>{@link #images}：配图 JSON 数组（COS 绝对地址，≤3 张，见 {@code JsonListUtil}）。</li>
 * </ul>
 * 口径真源：docs/schema/feedback.md。
 */
@Data
@TableName("feedback")
@Schema(description = "意见反馈")
public class Feedback {

    @TableId(type = IdType.AUTO)
    @Schema(description = "反馈ID")
    private Long id;

    /** 提交人用户ID（外键 {@code fk_feedback_user} → {@code user(id)}，恒为真实登录用户） */
    @Schema(description = "提交人用户ID")
    private Long userId;

    /** 反馈类型（`feedback_option` 字典项 ID；提交时校验该字典项存在且启用） */
    @Schema(description = "反馈类型（feedback_option 字典项 ID）")
    private Long optionId;

    @Schema(description = "反馈内容")
    private String content;

    /** 反馈配图 URL 列表 JSON（COS 绝对地址，≤3 张；落库为 JSON 字符串，见 JsonListUtil） */
    @Schema(description = "反馈配图URL列表JSON（COS 绝对地址，≤3 张）")
    private String images;

    /** 创建时间（DB 时钟：INSERT 由 `DEFAULT CURRENT_TIMESTAMP` 写入，应用层不写、不填充） */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
