package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价视图对象（VO）—— **公开视角，恰 8 字段**。
 * <p>
 * 公开评价列表（{@code GET /dishes/{id}/reviews}）恰返回 8 字段：
 * {@code id}、{@code userId}、{@code userNickname}、{@code userAvatar}、
 * {@code rating}、{@code content}、{@code images}、{@code createdAt}。
 * <p>
 * 字段集**严格限定公开视角**：{@code dishId} / {@code dishName} / {@code isHidden} 三字段
 * 归 {@link MyReviewVO}（本人视角）—— 「靠调用接口区分字段集」会让消费端写出
 * 「类型说有、实际没有」的不安全类型。
 */
@Data
@Schema(description = "评价展示信息（公开视角，8 字段）")
public class ReviewVO {

    @Schema(description = "评价ID")
    private Long id;

    @Schema(description = "评价者用户ID")
    private Long userId;

    @Schema(description = "评价者昵称", example = "张三")
    private String userNickname;

    @Schema(description = "评价者头像URL")
    private String userAvatar;

    @Schema(description = "评分（1-5星）")
    private Integer rating;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "评价配图 URL 列表（COS 绝对地址，≤3 张；无图返回空列表）")
    private List<String> images;

    @Schema(description = "评价时间")
    private LocalDateTime createdAt;
}
