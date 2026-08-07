package com.bjtufood.review.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review_useful")
public class ReviewUseful {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long reviewId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
