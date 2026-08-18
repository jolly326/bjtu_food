package com.bjtufood.moment.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态评论实体类（含一层回复）
 * <p>
 * 对应数据库表：moment_comment
 */
@Data
@TableName("moment_comment")
@Schema(description = "动态评论")
public class MomentComment {

    @TableId(type = IdType.AUTO)
    @Schema(description = "评论ID")
    private Long id;

    @Schema(description = "所属动态ID")
    private Long momentId;

    @Schema(description = "评论者用户ID")
    private Long userId;

    /** 父评论ID（一层回复：NULL=顶级评论，非NULL=对某评论的回复） */
    @Schema(description = "父评论ID（一层回复）")
    private Long parentId;

    @Schema(description = "评论正文")
    private String content;

    /** 评论图片（JSON 数组字符串，最多 3 张；与 Dish.images 一致存储） */
    @Schema(description = "评论图片（JSON 数组字符串，最多 3 张）")
    private String images;

    /** 👍 有用计数（task-12.4，一人一票，uk_useful_user_comment 维护） */
    @Schema(description = "👍 有用计数")
    private Integer usefulCount;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
