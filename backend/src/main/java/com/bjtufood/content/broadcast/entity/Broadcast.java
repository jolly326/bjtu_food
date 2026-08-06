package com.bjtufood.content.broadcast.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页广播通知条实体类
 * <p>
 * 对应数据库表：broadcast
 * 首页竖直翻滚 ticker 的数据来源，按 broadcastType 分发跳转（不写死社区）。
 */
@Data
@TableName("broadcast")
@Schema(description = "首页广播通知")
public class Broadcast {

    @TableId(type = IdType.AUTO)
    @Schema(description = "广播ID")
    private Long id;

    /** 广播标题 */
    @Schema(description = "广播标题")
    private String title;

    /** 广播正文（首页 ticker 展示文本） */
    @Schema(description = "广播正文")
    private String content;

    /**
     * 广播类型：NOTICE（通知）/ ACTIVITY（活动）/ DISH（菜品）/ URL（外链）/ NONE（无跳转）
     * 首页按此字段分发跳转逻辑。
     */
    @Schema(description = "广播类型：NOTICE/ACTIVITY/DISH/URL/NONE", example = "NOTICE")
    private String broadcastType;

    /** 跳转目标ID（broadcast_type=DISH 时填菜品ID） */
    @Schema(description = "跳转目标ID（DISH 类型时填菜品ID）")
    private Long targetId;

    /** 跳转目标URL（broadcast_type=URL 时填外链） */
    @Schema(description = "跳转目标URL（URL 类型时填外链）")
    private String targetUrl;

    /** 排序权重（越小越靠前） */
    @Schema(description = "排序权重（越小越靠前）")
    private Integer sortOrder;

    /** 状态：enabled（展示）/ disabled（隐藏） */
    @Schema(description = "状态：enabled/disabled", example = "enabled")
    private String status;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
