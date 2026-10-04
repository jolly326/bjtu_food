package com.bjtufood.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 站内通知投递入参（跨域契约）。
 * <p>
 * 架构收口 P0-1：业务域（feedback / correction / review）此前直接构造
 * {@code notify.entity.Notification} 实体传给 {@code NotificationService.notify(...)}，
 * 使两个模块 import 了 notify 的实体。现统一以本 DTO 传参，实体只在 notify 内部出现
 * （{@code isRead} 恒由实现侧置 0，调用方无需也不应关心）。
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
