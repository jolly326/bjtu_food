package com.bjtufood.moment.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态评论「有用 👍」标记实体（task-12.4）
 * <p>
 * 一人一票，唯一键 uk_useful_user_comment(user_id, comment_id)。
 * 对应数据库表：moment_comment_useful
 */
@Data
@TableName("moment_comment_useful")
@Schema(description = "动态评论有用标记")
public class MomentCommentUseful {

    @TableId(type = IdType.AUTO)
    @Schema(description = "标记ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "评论ID")
    private Long commentId;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
