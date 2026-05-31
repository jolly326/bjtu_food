package com.bjtufood.review.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价实体类
 * <p>
 * 对应数据库表：review
 * 学生就餐后对菜品发表的图文评价
 */
@Data
@TableName("review")
@Schema(description = "评价")
public class Review {

    @TableId(type = IdType.AUTO)
    @Schema(description = "评价ID")
    private Long id;

    /** 评价者用户ID */
    @Schema(description = "评价者用户ID")
    private Long userId;

    /** 被评价菜品ID */
    @Schema(description = "被评价菜品ID")
    private Long dishId;

    /** 评分（1-5星） */
    @Schema(description = "评分（1-5星）", example = "4")
    private Integer rating;

    /** 文字评价内容 */
    @Schema(description = "评价内容")
    private String content;

    /** 评价图片，JSON数组：["url1","url2"] */
    @Schema(description = "评价图片URL数组")
    private String images;

    /** 管理员隐藏标记（0=正常, 1=隐藏） */
    @Schema(description = "是否隐藏（0=正常, 1=管理员隐藏）")
    private Integer isHidden;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
