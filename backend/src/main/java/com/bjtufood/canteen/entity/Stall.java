package com.bjtufood.canteen.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 档口实体类
 * <p>
 * 对应数据库表：stall
 * 例如：第一食堂下属的"面食窗口"、"盖饭窗口"
 */
@Data
@TableName("stall")
@Schema(description = "档口")
public class Stall {

    @TableId(type = IdType.AUTO)
    @Schema(description = "档口ID")
    private Long id;

    /** 所属食堂ID */
    @Schema(description = "所属食堂ID")
    private Long canteenId;

    /** 档口名称 */
    @Schema(description = "档口名称", example = "面食窗口")
    private String name;

    /** 档口多图，JSON 字符串 */
    @Schema(description = "档口多图JSON")
    private String images;

    /** 档口位置 */
    @Schema(description = "档口位置")
    private String location;

    /** 楼层（如 1F/2F） */
    @Schema(description = "楼层（如 1F/2F）", example = "1F")
    private String floor;

    /** 窗口号 */
    @Schema(description = "窗口号", example = "3号窗口")
    private String windowNo;

    /** 营业时间，如 10:00-20:00 */
    @Schema(description = "营业时间，如 10:00-20:00", example = "10:00-20:00")
    private String businessHours;

    /** 档口描述 */
    @Schema(description = "档口描述")
    private String description;

    /** 排序权重 */
    @Schema(description = "排序权重")
    private Integer sortOrder;

    /** 状态：open / closed */
    @Schema(description = "状态", example = "open")
    private String status;

    /**
     * 审核状态（与启停 status 解耦）：pending（待审核）/ approved（已通过）/ rejected（已退回）
     * 后台录入默认 approved；学生 UGC 提交写入 pending。
     */
    @Schema(description = "审核状态：pending/approved/rejected", example = "approved")
    private String auditStatus;

    /** 退回原因（仅 audit_status=rejected 时由后台填写，可空） */
    @Schema(description = "退回原因（audit_status=rejected 时由后台填写）")
    private String rejectReason;

    /** 提交人用户ID（UGC 由当前登录用户写入，禁止前端传入） */
    @Schema(description = "提交人用户ID")
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
