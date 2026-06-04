package com.bjtufood.canteen.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轮播图实体类
 * <p>
 * 对应数据库表：banner
 * 首页展示的轮播图，支持跳转菜品详情或外部链接
 */
@Data
@TableName("banner")
@Schema(description = "轮播图")
public class Banner {

    @TableId(type = IdType.AUTO)
    @Schema(description = "轮播图ID")
    private Long id;

    /** 标题 */
    @Schema(description = "标题", example = "交大美食季")
    private String title;

    /** 副标题 */
    @Schema(description = "副标题", example = "发现校园里的每一道美味")
    private String subtitle;

    /** 背景图片 URL 列表，JSON 字符串 */
    @Schema(description = "背景图片URL列表JSON")
    private String images;

    /** 跳转类型：dish / url */
    @Schema(description = "跳转类型", example = "dish")
    private String type;

    /** 跳转目标ID（type=dish时使用） */
    @Schema(description = "跳转目标ID")
    private Long targetId;

    /** 跳转目标URL（type=url时使用） */
    @Schema(description = "跳转目标URL")
    private String targetUrl;

    /** 关联食堂ID（可选） */
    @Schema(description = "关联食堂ID")
    private Long canteenId;

    /** 排序权重 */
    @Schema(description = "排序权重")
    private Integer sortOrder;

    /** 状态：enabled / disabled */
    @Schema(description = "状态", example = "enabled")
    private String status;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
