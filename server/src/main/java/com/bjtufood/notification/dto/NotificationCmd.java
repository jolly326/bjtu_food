package com.bjtufood.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 站内通知投递入参（跨域契约）。
 * <p>
 * 业务域（moderation 的处置回执 / review 的评价隐藏回执）统一以本 DTO 传参，**不 import** notify 实体 ——
 * 实体只在 notify 内部出现（{@code isRead} 恒由实现侧置 0，调用方无需也不应关心）。
 * <p>
 * 字段名与 `notification.reply_title` / `reply_content` 一一对应：处置回执两列与
 * `moderation_task` 的同名列**同句两份快照**，一眼对得上（受理与投递判据互相独立，不作同步要求）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCmd {

    /** 接收人（user.id） */
    private Long userId;

    /** 标题 */
    private String replyTitle;

    /** 正文 */
    private String replyContent;
}
