package com.bjtufood.history.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 访问日志实体类（view_log，**append-only**）
 * <p>
 * 对应数据库表：view_log —— 每次菜品浏览 INSERT 一行（不做更新与合并），
 * 游客 {@code user_id=0}。供时间窗口聚合（近 N 天最热菜品）与管理端行为查看读取；
 * {@code dish.view_count}（全历史累计聚合列）由详情成功路径原子自增维护，与本表并存不混用。
 */
@Data
@TableName("view_log")
@Schema(description = "访问日志")
public class ViewLog {

    @TableId(type = IdType.AUTO)
    @Schema(description = "日志ID")
    private Long id;

    /** 浏览者用户ID（0 = 游客） */
    @Schema(description = "浏览者用户ID（0=游客）")
    private Long userId;

    /** 浏览对象类型：dish / stall / canteen */
    @Schema(description = "浏览对象类型：dish/stall/canteen")
    private String targetType;

    @Schema(description = "浏览对象ID")
    private Long targetId;

    /** 访问时间（INSERT 时由填充器以 JVM 时钟写入；append-only 后不再有更新时间语义） */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "访问时间")
    private LocalDateTime createdAt;
}
