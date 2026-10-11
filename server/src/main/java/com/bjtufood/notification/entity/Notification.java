package com.bjtufood.notification.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知实体类
 * <p>
 * 对应数据库表：notification
 */
@Data
@TableName("notification")
@Schema(description = "消息通知")
public class Notification {

    @TableId(type = IdType.AUTO)
    @Schema(description = "通知ID")
    private Long id;

    @Schema(description = "接收用户ID")
    private Long userId;

    /** 通知标题（与 `moderation_task.reply_title` 同名列：处置回执为同句两份快照） */
    @Schema(description = "通知标题")
    private String replyTitle;

    /** 通知正文（与 `moderation_task.reply_content` 同名列：处置回执为同句两份快照） */
    @Schema(description = "通知正文")
    private String replyContent;

    /** 是否已读：0=未读 1=已读 */
    @Schema(description = "是否已读：0=未读 1=已读")
    private Integer isRead;

    /** 创建时间（DB 时钟：INSERT 由 `DEFAULT CURRENT_TIMESTAMP` 写入，应用层不写、不填充） */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
