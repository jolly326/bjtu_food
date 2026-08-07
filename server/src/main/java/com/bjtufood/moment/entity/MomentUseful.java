package com.bjtufood.moment.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态「有用 👍」标记实体（一人一票，uk_useful_user_moment）
 */
@Data
@TableName("moment_useful")
@Schema(description = "动态有用标记")
public class MomentUseful {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    @TableField("moment_id")
    private Long momentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
