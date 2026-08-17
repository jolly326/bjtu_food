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

    /** 反馈类型：suggestion / error / other / report */
    @Schema(description = "反馈类型：suggestion/error/other/report")
    private String type;

    @Schema(description = "反馈内容")
    private String content;

    @Schema(description = "联系方式")
    private String contact;

    /** 附图（JSON 数组字符串，如 ["/uploads/xx.jpg"]；截图/作证照片/菜品图，2026-08-17 新增） */
    @Schema(description = "附图（JSON 数组字符串，存绝对URL）")
    private String images;

    /** 关联类型（社区举报场景）：moment（关联被举报动态）；其他反馈为 null */
    @Schema(description = "关联类型（举报场景）：moment；其他为 null")
    private String relatedType;

    /** 关联对象ID（举报场景：被举报动态ID）；其他反馈为 null */
    @Schema(description = "关联对象ID（举报场景：动态ID）；其他为 null")
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
