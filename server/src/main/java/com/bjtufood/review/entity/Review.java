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
     * 内容安全状态 sec_state 已取消人工复核：
     * 机检 pass/review 一律放行、risky 直接拒绝（不落库），故无安全态可存。
     */

    /** 管理员隐藏标记（0=正常, 1=隐藏） */
    @Schema(description = "是否隐藏（0=正常, 1=管理员隐藏）")
    private Integer isHidden;

    /**
     * 隐藏附注（管理员可选填写，≤200 字；随隐藏回执下发给作者）。
     * <p>
     * `is_hidden = 0`（恢复显示）时置 NULL —— 避免旧附注残留到下一条回执。
     */
    @Schema(description = "隐藏附注（≤200 字，随隐藏回执下发；未隐藏为 null）")
    private String hiddenNote;

    /**
     * 发表时间（**重复提交保留原值**，覆盖不刷新 ⇒ 评价位置固定）。
     * <p>
     * DB 时钟：仅 INSERT 由 `DEFAULT CURRENT_TIMESTAMP` 写入（应用层不写、不填充）；
     * 本表<b>无 {@code updated_at}</b>，故 UPDATE 不会刷新本列。
     */
    @Schema(description = "发表时间（**重复提交保留原值**，覆盖不刷新 ⇒ 评价位置固定）")
    private LocalDateTime createdAt;
}
