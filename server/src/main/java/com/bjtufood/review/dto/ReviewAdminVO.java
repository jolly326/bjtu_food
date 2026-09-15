package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价视图对象（管理端专用 VO）
 * <p>
 * 管理端独有语义字段为 {@code isHidden}（是否被隐藏，仅管理端可见/可改）。
 * 内容安全态 {@code secState} 已随「取消人工复核」（2026-09-15 用户拍板）全链退役，不再返回。
 */
@Data
@Schema(description = "评价展示信息（管理端专用，含审核标记）")
public class ReviewAdminVO {

    @Schema(description = "评价ID")
    private Long id;

    @Schema(description = "评价者用户ID")
    private Long userId;

    @Schema(description = "关联菜品ID")
    private Long dishId;

    @Schema(description = "关联菜品名称")
    private String dishName;

    @Schema(description = "评价者昵称", example = "张三")
    private String userNickname;

    @Schema(description = "评价者头像URL")
    private String userAvatar;

    @Schema(description = "评分（1-5星）")
    private Integer rating;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "评价配图 URL 列表（COS 绝对地址，≤3 张）")
    private List<String> images;

    @Schema(description = "评价时间")
    private LocalDateTime createdAt;

    @Schema(description = "是否被隐藏（管理端用，0/1）")
    private Integer isHidden;

    @Schema(description = "「有用」标记总数", example = "3")
    private Integer usefulCount;
}
