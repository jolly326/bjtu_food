package com.bjtufood.review.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价视图对象（VO）
 * <p>
 * 前端展示的评价信息，包含评价者基本信息
 */
@Data
@Schema(description = "评价展示信息")
public class ReviewVO {

    @Schema(description = "评价ID")
    private Long id;

    @Schema(description = "评价者用户ID")
    private Long userId;

    @Schema(description = "关联菜品ID")
    private Long dishId;

    @Schema(description = "关联菜品名称（我的评价列表等场景由 mapper 联表补齐）")
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

    /**
     * 内容安全状态：pass / review。
     * 仅当查看者=作者本人时可能返回 review（机检待人工复核，前端提示「审核中」）；
     * 他端可见的评价恒为 pass（review/rejected 已在后端过滤，不外泄审核态）。
     */
    @Schema(description = "内容安全状态：pass/review（review 仅作者本人可见，前端提示「审核中」）")
    private String secState;

    /** 配图 JSON 原文（mapper 直填，service 层解析为 images；不对外输出） */
    @JsonIgnore
    @Schema(hidden = true)
    private String imagesJson;

    @Schema(description = "评价时间")
    private LocalDateTime createdAt;

    /** 「有用」标记总数（冗余计数） */
    @Schema(description = "「有用」标记总数", example = "3")
    private Integer usefulCount;

    /** 当前登录用户是否已标记「有用」（仅登录态返回；公开列表可为 null，以免泄露） */
    @Schema(description = "当前用户是否已标记「有用」（仅登录态返回）")
    private Boolean useful;

    /**
     * 是否被管理员隐藏（0=正常 / 1=已被隐藏）。
     * <p>
     * 仅「我的评价」本人视角填充（用于端上标注「已被隐藏」），公开评价列表恒为 null
     * （公开列表已在 SQL 层过滤 is_hidden=1，不会看到隐藏内容，也不对外暴露该字段语义）。
     */
    @Schema(description = "是否被管理员隐藏：0=正常/1=已隐藏（仅「我的评价」本人视角返回）", example = "0")
    private Integer isHidden;
}
