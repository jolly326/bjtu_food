package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价视图对象（管理端专用 VO）
 * <p>
 * 相比公开 {@link ReviewVO}，额外携带管理端审核所需的 {@code isHidden}/{@code hasSensitive}/{@code secState} 字段。
 * 公开接口严禁返回 isHidden/hasSensitive（见 spec §3.x.6.4）。
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

    @Schema(description = "内容安全状态：pass/review/rejected（管理端复核用）")
    private String secState;

    @Schema(description = "评价时间")
    private LocalDateTime createdAt;

    @Schema(description = "是否包含敏感词（管理端标记用）")
    private Boolean hasSensitive;

    @Schema(description = "是否被隐藏（管理端用，0/1）")
    private Integer isHidden;

    @Schema(description = "「有用」标记总数", example = "3")
    private Integer usefulCount;
}
