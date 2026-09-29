package com.bjtufood.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知视图对象（{@code GET /my/notifications} 单行出参）。
 * <p>
 * <b>出参恰 5 字段</b>：{@code id} / {@code title} / {@code content} / {@code isRead} / {@code createdAt}。
 * <p>
 * 以下字段<b>不出参</b>（端上零消费，按「零消费即删」原则不下发）：
 * <ul>
 *   <li>{@code type}（通知类型键 {@code feedback_handle} / {@code correction_handle}）——
 *       端上通知卡只渲染「标题 + 正文 + 时间 + 未读态」，**从不按类型分支、不做类型相关跳转**
 *       （见 docs/ui/client-系统通知.md：该字段标注为「无界面」）；类型仅服务端内部使用。
 *       若将来要做「按类型跳转」，须先由 UI 文档定义交互，再以 {@code targetType/targetId} 形式扩字段
 *       （不为「以后可能用到」预埋）；</li>
 *   <li>{@code relatedId}（关联反馈 ID）——同样零消费、当前无落地页。</li>
 * </ul>
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
