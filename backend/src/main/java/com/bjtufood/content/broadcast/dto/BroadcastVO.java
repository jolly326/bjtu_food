package com.bjtufood.content.broadcast.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页广播通知视图对象（VO）
 * <p>
 * 首页竖直翻滚 ticker 展示，按 broadcastType 分发跳转。
 */
@Data
@Schema(description = "首页广播通知展示信息")
public class BroadcastVO {

    @Schema(description = "广播ID")
    private Long id;

    @Schema(description = "广播标题")
    private String title;

    @Schema(description = "广播正文（ticker 展示文本）")
    private String content;

    /** 广播类型：NOTICE/ACTIVITY/DISH/URL/NONE（首页按类型分发跳转） */
    @Schema(description = "广播类型：NOTICE/ACTIVITY/DISH/URL/NONE", example = "NOTICE")
    private String broadcastType;

    @Schema(description = "跳转目标ID（DISH 类型时填菜品ID）")
    private Long targetId;

    @Schema(description = "跳转目标URL（URL 类型时填外链）")
    private String targetUrl;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
