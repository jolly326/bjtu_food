package com.bjtufood.canteen.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 档口实体类
 * <p>
 * 对应数据库表：stall。2026-09-14 用户拍板：档口已<b>去实体化</b>，降级为「菜品筛选属性字典」，
 * 生命周期仅「新增 / 改名（编辑）」，不再具备删除、停业/营业状态、营业时间、实体审核等实体语义能力。
 * 原 {@code status}（停业语义）/ {@code audit_status}（实体审核语义）/ {@code reject_reason} 字段
 * 已从实体与接口层移除（不再读写）；对应数据库列是否 DROP 另行评估，见任务回报的列下线方案。
 * {@code floor}/{@code window_no} 属字典描述字段（端上有消费），保留。
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

    /** 档口描述 */
    @Schema(description = "档口描述")
    private String description;

    /** 排序权重 */
    @Schema(description = "排序权重")
    private Integer sortOrder;

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
