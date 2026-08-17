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

    /** 父评价ID（NULL=顶层评价，非NULL=回复） */
    @Schema(description = "父评价ID（NULL=顶层评价）")
    private Long parentId;

    /** 被回复者昵称（仅回复记录有值，用于展示「@昵称」） */
    @Schema(description = "被回复者昵称（仅回复记录有值）")
    private String replyToNickname;

    /** 楼中楼子回复（按 created_at 升序；顶层评价附带，子回复本身 replies 为 null） */
    @Schema(description = "楼中楼子回复列表（顶层评价附带，按创建时间升序）")
    private List<ReviewVO> replies;

    /** 该节点是否有更多子回复（当前窗口只返回最近 N 条，true 表示还有更多，前端展示「查看全部」占位） */
    @Schema(description = "该节点是否有更多子回复（窗口限制后仍有更多，供前端展示「查看全部」）")
    private Boolean repliesHasMore;
}
