package com.bjtufood.review.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价实体类
 * <p>
 * 对应数据库表：review
 * 学生就餐后对菜品发表的文字评价
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

    /** 评价配图 URL 列表 JSON（COS 绝对地址，≤3 张；落库为 JSON 字符串，见 JsonListUtil） */
    @Schema(description = "评价配图URL列表JSON（COS 绝对地址，≤3 张）")
    private String images;

    /*
     * 内容安全状态 sec_state 已随「取消人工复核」（2026-09-15 用户拍板）全链退役：
     * 机检 pass/review 一律放行、risky 直接拒绝（不落库），故无安全态可存。
     */

    /** 管理员隐藏标记（0=正常, 1=隐藏） */
    @Schema(description = "是否隐藏（0=正常, 1=管理员隐藏）")
    private Integer isHidden;

    // review.useful_count 冗余计数列与 review_useful 表已于 2026-09-20 整链下线
    // （「评价有用」能力删除），实体字段同批移除，避免 MP 读写不存在的列。

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
