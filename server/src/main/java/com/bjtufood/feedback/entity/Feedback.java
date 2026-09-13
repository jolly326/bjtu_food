package com.bjtufood.feedback.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户反馈实体类（升级后的 user_feedback 映射）
 * <p>
 * 对应数据库表：user_feedback
 */
@Data
@TableName("user_feedback")
@Schema(description = "用户反馈")
public class Feedback {

    @TableId(type = IdType.AUTO)
    @Schema(description = "反馈ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    /** 反馈类型：suggestion / add / error / bug / report / other（与 FeedbackConst 保持一致） */
    @Schema(description = "反馈类型：suggestion/add/error/bug/report/other")
    private String type;

    @Schema(description = "反馈内容")
    private String content;

    /** 反馈配图 URL 列表 JSON（COS 绝对地址，≤3 张；落库为 JSON 字符串，见 JsonListUtil） */
    @Schema(description = "反馈配图URL列表JSON（COS 绝对地址，≤3 张）")
    private String images;

    /**
     * 内容安全状态（产品定稿 2026-09-13）：pass / review / rejected。
     * 反馈无公开展示，仅作管理端复核标记（列表 secState 筛选）。
     */
    @Schema(description = "内容安全状态：pass/review/rejected（仅管理端复核标记）")
    private String secState;

    @Schema(description = "联系方式")
    private String contact;

    /** 关联类型：report 举报为 review（被举报评价）；error 信息纠错为 dish；其他反馈为 null */
    @Schema(description = "关联类型：举报为 review；信息纠错为 dish；其他为 null")
    private String relatedType;

    /** 关联对象ID（举报场景：被举报评价ID；信息纠错：菜品ID）；其他反馈为 null */
    @Schema(description = "关联对象ID（举报：评价ID；信息纠错：菜品ID）；其他为 null")
    private Long relatedId;

    /** 处理状态：pending / handled */
    @Schema(description = "处理状态：pending/handled")
    private String status;

    /** 管理员回复/处理说明 */
    @Schema(description = "管理员回复")
    private String reply;

    /** 处理时间 */
    @Schema(description = "处理时间")
    private LocalDateTime handledAt;

    /** 处理人管理员ID */
    @Schema(description = "处理人管理员ID")
    private Long handlerId;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
