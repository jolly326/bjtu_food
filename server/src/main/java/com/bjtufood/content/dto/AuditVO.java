package com.bjtufood.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审核列表视图对象（后台 UGC 审核）
 * <p>
 * 统一承载 dish / stall / canteen 三类待审内容的关键信息。
 */
@Data
@Schema(description = "审核列表项")
public class AuditVO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "审核对象类型：dish / stall / canteen")
    private String type;

    @Schema(description = "名称（菜品名/档口名/食堂名）")
    private String name;

    @Schema(description = "价格（分），仅菜品有")
    private Integer price;

    @Schema(description = "位置/营业时间等补充信息")
    private String location;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "图片URL列表（绝对地址）")
    private List<String> images;

    @Schema(description = "关联食堂ID（档口/菜品）")
    private Long canteenId;

    @Schema(description = "关联食堂名称")
    private String canteenName;

    @Schema(description = "关联档口ID（菜品）")
    private Long stallId;

    @Schema(description = "关联档口名称")
    private String stallName;

    @Schema(description = "审核状态：pending/approved/rejected")
    private String auditStatus;

    @Schema(description = "退回原因")
    private String rejectReason;

    @Schema(description = "提交人用户ID")
    private Long createdBy;

    @Schema(description = "提交人昵称")
    private String submitterName;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
