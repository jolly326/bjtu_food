package com.bjtufood.apply.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体贡献统一申请表（task-12.1）
 * <p>
 * 菜品/档口/食堂的「新增/下架/变更」三类申请，走独立统一审核状态机，
 * 不复用 dish/stall/canteen 实体的 audit_status（见 spec §0.4 / §3.x.1）。
 * 对应数据库表：apply_action
 */
@Data
@TableName("apply_action")
@Schema(description = "实体贡献统一申请")
public class ApplyAction {

    @TableId(type = IdType.AUTO)
    @Schema(description = "申请ID")
    private Long id;

    @Schema(description = "申请人用户ID（学生）")
    private Long applicantId;

    /** 实体类型：DISH / STALL / CANTEEN */
    @Schema(description = "实体类型：DISH/STALL/CANTEEN")
    private String entityType;

    /** 关联实体ID（新增类申请可空，待审核通过后回填） */
    @Schema(description = "关联实体ID（新增类可空）")
    private Long entityId;

    /** 申请类型：NEW / CLOSE / CHANGE */
    @Schema(description = "申请类型：NEW/CLOSE/CHANGE")
    private String applyType;

    /** 审核状态：pending / approved / rejected */
    @Schema(description = "审核状态：pending/approved/rejected")
    private String status;

    /** 申请字段快照（JSON） */
    @Schema(description = "申请字段快照（JSON）")
    private String payload;

    /** 退回原因（rejected 时必填） */
    @Schema(description = "退回原因（rejected 时必填）")
    private String rejectReason;

    /** 处理人管理员ID */
    @Schema(description = "处理人管理员ID")
    private Long handledBy;

    /** 处理时间 */
    @Schema(description = "处理时间")
    private LocalDateTime handledAt;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
