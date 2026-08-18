package com.bjtufood.moment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布评论请求（支持一层回复）
 */
@Data
@Schema(description = "发布评论请求")
public class MomentCommentReq {

    @Schema(description = "评论正文（纯图评论可空，但 content/images 至少一项）", example = "看起来不错！")
    @Size(max = 500, message = "评论不能超过500字")
    private String content;

    /** 评论图片（最多 3 张，前端已限制） */
    @Schema(description = "评论图片（最多 3 张）")
    private List<String> images;

    /** 父评论ID（一层回复：非空表示回复某评论） */
    @Schema(description = "父评论ID（一层回复）")
    private Long parentId;
}
