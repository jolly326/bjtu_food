package com.bjtufood.history.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 浏览足迹实体类
 * <p>
 * 对应数据库表：view_log，同时供「猜你喜欢」个性化读取（唯一存储）
 */
@Data
@TableName("view_log")
@Schema(description = "浏览足迹")
public class ViewLog {

    @TableId(type = IdType.AUTO)
    @Schema(description = "足迹ID")
    private Long id;

    @Schema(description = "浏览者用户ID")
    private Long userId;

    /** 浏览对象类型：dish / stall / canteen / moment */
    @Schema(description = "浏览对象类型：dish/stall/canteen/moment")
    private String targetType;

    @Schema(description = "浏览对象ID")
    private Long targetId;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "浏览时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
