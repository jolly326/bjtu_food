package com.bjtufood.moment.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社区动态实体类
 * <p>
 * 对应数据库表：moment
 */
@Data
@TableName("moment")
@Schema(description = "社区动态")
public class Moment {

    @TableId(type = IdType.AUTO)
    @Schema(description = "动态ID")
    private Long id;

    @Schema(description = "发布者用户ID")
    private Long userId;

    @Schema(description = "动态正文")
    private String content;

    /** 动态图片URL列表，逗号分隔（≤9张） */
    @Schema(description = "动态图片URL列表（逗号分隔）")
    private String images;

    /** 关联对象类型：dish / stall / none */
    @Schema(description = "关联对象类型：dish/stall/none")
    private String relatedType;

    /** 关联对象ID（dish_id 或 stall_id），related_type=none 时为 null */
    @Schema(description = "关联对象ID")
    private Long relatedId;

    /** 审核状态：pending/approved/rejected */
    @Schema(description = "审核状态：pending/approved/rejected")
    private String auditStatus;

    /** 退回原因（rejected 时由后台填写） */
    @Schema(description = "退回原因")
    private String rejectReason;

    /** 「有用 👍」标记数（一人一票） */
    @Schema(description = "有用标记数")
    private Integer usefulCount;

    /** 评论数（冗余计数） */
    @Schema(description = "评论数")
    private Integer commentCount;

    /** 下架状态：0=正常 1=管理员强制下架 */
    @Schema(description = "下架状态：0=正常 1=下架")
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
