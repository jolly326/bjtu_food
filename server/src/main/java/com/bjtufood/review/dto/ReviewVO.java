package com.bjtufood.review.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @Schema(description = "关联菜品ID")
    private Long dishId;

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

    @JsonIgnore
    @Schema(hidden = true)
    private String imagesJson;

    @Schema(description = "评价时间")
    private LocalDateTime createdAt;

    /** 「有用」标记总数（冗余计数） */
    @Schema(description = "「有用」标记总数", example = "3")
    private Integer usefulCount;

    /** 当前登录用户是否已标记「有用」（仅登录态返回；公开列表可为 null，以免泄露） */
    @Schema(description = "当前用户是否已标记「有用」（仅登录态返回）")
    private Boolean useful;
}
