package com.bjtufood.moment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态评论视图对象（一层回复扁平化返回）
 */
@Data
@Schema(description = "动态评论展示信息")
public class MomentCommentVO {

    @Schema(description = "评论ID")
    private Long id;

    @Schema(description = "所属动态ID")
    private Long momentId;

    @Schema(description = "评论者用户ID")
    private Long userId;

    @Schema(description = "评论者昵称")
    private String userNickname;

    @Schema(description = "评论者头像URL")
    private String userAvatar;

    @Schema(description = "父评论ID（一层回复）")
    private Long parentId;

    /** 父评论昵称（一层回复展示用） */
    @Schema(description = "父评论昵称（回复 @昵称）")
    private String replyToNickname;

    @Schema(description = "评论正文")
    private String content;

    @Schema(description = "👍 有用计数")
    private Integer usefulCount;

    @Schema(description = "当前用户是否已点👍（仅登录用户）")
    private Boolean useful;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
