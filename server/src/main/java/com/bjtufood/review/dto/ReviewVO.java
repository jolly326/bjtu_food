package com.bjtufood.review.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价视图对象（VO）
 * <p>
 * 公开评价列表（{@code GET /dishes/{id}/reviews}）恰返回 8 字段：
 * {@code id}、{@code userId}、{@code userNickname}、{@code userAvatar}、
 * {@code rating}、{@code content}、{@code images}、{@code createdAt}。
 * <p>
 * {@code dishId} / {@code dishName} / {@code isHidden} 为作者视角字段，
 * 仅「我的评价」（{@code GET /my/reviews}）返回；公开列表不查询这三列，
 * 值为 null 时经 {@link JsonInclude} 从响应中省略。
 */
@Data
@Schema(description = "评价展示信息")
public class ReviewVO {

    @Schema(description = "评价ID")
    private Long id;

    @Schema(description = "评价者用户ID")
    private Long userId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "关联菜品ID（仅「我的评价」返回）")
    private Long dishId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "关联菜品名称（仅「我的评价」返回，mapper 联表补齐）")
    private String dishName;

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

    /** 配图 JSON 原文（mapper 直填，service 层解析为 images；不对外输出） */
    @JsonIgnore
    @Schema(hidden = true)
    private String imagesJson;

    @Schema(description = "评价时间")
    private LocalDateTime createdAt;

    /**
     * 是否被管理员隐藏（0=正常 / 1=已被隐藏）。
     * <p>
     * 仅「我的评价」本人视角填充（用于端上标注「已被隐藏」）；公开评价列表不返回该字段
     * （公开列表已在 SQL 层过滤 is_hidden=0，且归属由路径表达，不对外暴露该语义）。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "是否被管理员隐藏：0=正常/1=已隐藏（仅「我的评价」本人视角返回）", example = "0")
    private Integer isHidden;
}
