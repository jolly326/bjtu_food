package com.bjtufood.moment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布评论请求（支持一层回复）
 */
@Data
@Schema(description = "发布评论请求")
public class MomentCommentReq {

    @Schema(description = "评论正文", example = "看起来不错！")
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论不能超过500字")
    private String content;

    /** 父评论ID（一层回复：非空表示回复某评论） */
    @Schema(description = "父评论ID（一层回复）")
    private Long parentId;
}
