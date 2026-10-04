package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 「我的评价」视图对象（VO）—— **本人视角，恰 7 字段**。
 * <p>
 * <b>与公开视角分型</b>（docs/func/client/README.md R9）：同一契约的两视角 MUST 是两个类型，
 * 端上 SHALL NOT 用单一 interface 复用，故本类<b>不继承</b> {@link ReviewVO}
 * （继承会让类型系统宣称「我的评价也有 userId / userNickname / userAvatar」，
 * 而这三个字段在本人视角恒等于本人、属零信息，不得下发）。
 * <p>
 * 字段构成 = 公开视角 {@link ReviewVO} 8 字段中去掉作者标识三项
 * （{@code userId} / {@code userNickname} / {@code userAvatar}）后的 5 项
 * （{@code id} / {@code rating} / {@code content} / {@code images} / {@code createdAt}）
 * + 本人视角专属 2 项（{@code dishId} / {@code dishName}）。
 * <p>
 * 逐字段口径以 {@code docs/api/client/dishes.md} 的公开评价字段表为唯一真源。
 */
@Data
@Schema(description = "我的评价展示信息（本人视角，7 字段）")
public class MyReviewVO {

    @Schema(description = "评价ID")
    private Long id;

    @Schema(description = "评分（1-5星）")
    private Integer rating;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "评价配图 URL 列表（COS 绝对地址，≤3 张；无图返回空列表）")
    private List<String> images;

    @Schema(description = "评价时间（重新评价后取新时间）")
    private LocalDateTime createdAt;

    /** 关联菜品ID（本人视角专属；公开列表不返回 —— 归属已由路径 / 列表上下文表达） */
    @Schema(description = "关联菜品ID（仅「我的评价」返回）")
    private Long dishId;

    /** 关联菜品名称（本人视角专属，mapper 联表 dish 补齐，供列表行展示） */
    @Schema(description = "关联菜品名称（仅「我的评价」返回，mapper 联表补齐）")
    private String dishName;
}
