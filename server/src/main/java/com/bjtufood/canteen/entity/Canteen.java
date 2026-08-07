package com.bjtufood.canteen.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 食堂实体类
 * <p>
 * 对应数据库表：canteen
 * 例如：第一食堂、第二食堂
 */
@Data
@TableName("canteen")
@Schema(description = "食堂")
public class Canteen {

    @TableId(type = IdType.AUTO)
    @Schema(description = "食堂ID")
    private Long id;

    /** 食堂名称 */
    @Schema(description = "食堂名称", example = "第一食堂")
    private String name;

    /** 食堂图片 URL 列表，JSON 字符串 */
    @Schema(description = "食堂图片URL列表JSON")
    private String images;

    /** 食堂位置 */
    @Schema(description = "食堂位置")
    private String location;

    /** 纬度（GCJ-02，距离排序用） */
    @Schema(description = "纬度（GCJ-02）")
    private BigDecimal latitude;

    /** 经度（GCJ-02，距离排序用） */
    @Schema(description = "经度（GCJ-02）")
    private BigDecimal longitude;

    /** 食堂描述 */
    @Schema(description = "食堂描述")
    private String description;

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

    /** 排序权重（数字越小越靠前） */
    @Schema(description = "排序权重")
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
