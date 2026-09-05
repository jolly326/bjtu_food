package com.bjtufood.moment.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态评论「有用 👍」标记实体（一人一票，uk_useful_user_comment；
 * moment-comment-thread-view 恢复，moment_comment.useful_count 由本表聚合维护）
 */
@Data
@TableName("moment_comment_useful")
@Schema(description = "动态评论有用标记")
public class MomentCommentUseful {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    @TableField("comment_id")
    private Long commentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
