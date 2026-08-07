package com.bjtufood.apply.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「我的提交」聚合视图对象（task-12.1，GET /my/submissions）
 * <p>
 * type=apply（实体贡献申请）或 type=moment（本人动态）。
 */
@Data
@Schema(description = "我的提交聚合项")
public class SubmissionVO {

    @Schema(description = "聚合类型：apply / moment")
    private String type;

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "实体类型（apply 类）：DISH/STALL/CANTEEN；moment 类为 null")
    private String entityType;

    @Schema(description = "动作（apply 类）：NEW/CLOSE/CHANGE；moment 类为 null")
    private String action;

    @Schema(description = "预览标题（apply 类取 payload 预览；moment 类取内容前缀）")
    private String title;

    @Schema(description = "状态：apply 类用 pending/approved/rejected；moment 类复用 auditStatus")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
