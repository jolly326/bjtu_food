package com.bjtufood.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知视图对象（{@code GET /my/notifications} 单行出参）。
 * <p>
 * <b>出参恰 5 字段</b>：{@code id} / {@code title} / {@code content} / {@code isRead} / {@code createdAt}。
 * 通知卡只渲染「标题 + 正文 + 时间 + 未读态」，**不按类型分支、不做类型相关跳转**（类型键与关联 ID 不落库，按「零消费即删」原则）。
 */
@Data
@Schema(description = "消息通知展示信息")
public class NotificationVO {

    @Schema(description = "通知ID")
    private Long id;

    @Schema(description = "通知标题")
    private String title;

    @Schema(description = "通知正文")
    private String content;

    @Schema(description = "是否已读：true=已读 / false=未读")
    private Boolean isRead;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
