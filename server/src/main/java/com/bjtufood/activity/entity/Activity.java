package com.bjtufood.activity.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 最新活动实体类（公众号文章卡片）
 * <p>
 * 对应数据库表：activity
 * 小程序「最新活动」页卡片列表数据来源；点击卡片经 web-view 打开公众号文章。
 */
@Data
@TableName("activity")
@Schema(description = "最新活动（公众号文章卡片）")
public class Activity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "活动ID")
    private Long id;

    /** 活动/文章标题 */
    @Schema(description = "活动/文章标题")
    private String title;

    /** 摘要（卡片副文案） */
    @Schema(description = "摘要（卡片副文案）")
    private String description;

    /** 封面图 URL（公众号文章封面，可空） */
    @Schema(description = "封面图 URL（可空）")
    private String image;

    /** 公众号文章链接（小程序 web-view 打开） */
    @Schema(description = "公众号文章链接")
    private String articleUrl;

    /** 展示状态：enabled / disabled */
    @Schema(description = "展示状态：enabled/disabled", example = "enabled")
    private String status;

    /** 排序权重（越小越靠前） */
    @Schema(description = "排序权重（越小越靠前）")
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
