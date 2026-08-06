package com.bjtufood.notify.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知视图对象
 */
@Data
@Schema(description = "消息通知展示信息")
public class NotificationVO {

    @Schema(description = "通知ID")
    private Long id;

    @Schema(description = "通知类型：moment_audit/dish_audit/comment/useful/activity")
    private String type;

    @Schema(description = "通知标题")
    private String title;

    @Schema(description = "通知正文")
    private String content;

    @Schema(description = "关联对象ID")
    private Long relatedId;

    @Schema(description = "是否已读：0=未读 1=已读")
    private Integer isRead;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
