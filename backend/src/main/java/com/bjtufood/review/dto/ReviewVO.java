package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价视图对象（VO）
 * <p>
 * 前端展示的评价信息，包含评价者基本信息
 */
@Data
@Schema(description = "评价展示信息")
public class ReviewVO {

    @Schema(description = "评价ID")
    private Long id;

    @Schema(description = "评价者用户ID")
    private Long userId;

    @Schema(description = "评价者昵称", example = "张三")
    private String userNickname;

    @Schema(description = "评价者头像URL")
    private String userAvatar;

    @Schema(description = "评分（1-5星）")
    private Integer rating;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "评价图片URL列表")
    private List<String> images;

    @Schema(description = "评价时间")
    private LocalDateTime createdAt;

    // ==================== 以下字段仅管理端返回 ====================

    @Schema(description = "是否包含敏感词（管理端标记用）")
    private Boolean hasSensitive;

    @Schema(description = "是否被隐藏（管理端用）")
    private Integer isHidden;
}
