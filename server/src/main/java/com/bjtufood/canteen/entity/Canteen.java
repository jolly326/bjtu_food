package com.bjtufood.canteen.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 食堂实体类
 * <p>
 * 对应数据库表：canteen。2026-09-14 用户拍板：食堂已<b>去实体化</b>，降级为「菜品筛选属性字典」，
 * 生命周期仅「新增 / 改名（编辑）」，不再具备删除、停业/营业状态、营业时间、实体审核等实体语义能力。
 * 原 {@code status}（停业语义）/ {@code audit_status}（实体审核语义）/ {@code reject_reason} 字段
 * 已从实体与接口层移除（不再读写）。
 * <p>
 * {@code created_by}（提交人）亦已退役（2026-09-15，阶段4）：食堂降级为「菜品筛选属性字典」后无归属语义，
 * 写入侧恒为系统占位值、三端零消费，实体字段与写入/列定义同批移除（存量库由 schema.sql 幂等 DROP）。
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
