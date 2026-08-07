package com.bjtufood.apply.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体贡献申请视图对象（task-12.1）
 */
@Data
@Schema(description = "实体贡献申请展示信息")
public class ApplyVO {

    @Schema(description = "申请ID")
    private Long id;

    @Schema(description = "申请人用户ID")
    private Long applicantId;

    @Schema(description = "实体类型：DISH/STALL/CANTEEN")
    private String entityType;

    @Schema(description = "关联实体ID（新增类审核通过前为 null）")
    private Long entityId;

    @Schema(description = "申请类型：NEW/CLOSE/CHANGE")
    private String applyType;

    @Schema(description = "审核状态：pending/approved/rejected")
    private String status;

    @Schema(description = "申请字段快照（JSON）")
    private String payload;

    @Schema(description = "退回原因（rejected 时）")
    private String rejectReason;

    @Schema(description = "处理人管理员ID")
    private Long handledBy;

    @Schema(description = "处理时间")
    private LocalDateTime handledAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
