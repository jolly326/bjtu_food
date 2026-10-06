package com.bjtufood.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 站内通知投递入参（跨域契约）。
 * <p>
 * 业务域（feedback / correction / review）统一以本 DTO 传参，**不 import** notify 实体 ——
 * 实体只在 notify 内部出现（{@code isRead} 恒由实现侧置 0，调用方无需也不应关心）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCmd {

    /** 接收人（user.id） */
    private Long userId;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;
}
