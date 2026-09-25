package com.bjtufood.notify.entity;

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

    /** 通知类型：feedback_handle=反馈处理回执 / correction_handle=菜品信息纠错回执 */
    @Schema(description = "通知类型")
    private String type;

    @Schema(description = "通知标题")
    private String title;

    @Schema(description = "通知正文")
    private String content;

    /** 关联对象ID（feedback_handle=反馈 ID / correction_handle=纠错 ID，按 type 解释） */
    @Schema(description = "关联对象ID")
    private Long relatedId;

    /** 是否已读：0=未读 1=已读 */
    @Schema(description = "是否已读：0=未读 1=已读")
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
