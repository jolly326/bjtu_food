package com.bjtufood.favorite.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏实体类
 * <p>
 * 对应数据库表：favorite
 * 用户与菜品之间的收藏关系，每人每菜只能收藏一次（UNIQUE约束）
 */
@Data
@TableName("favorite")
@Schema(description = "收藏关系")
public class Favorite {

    @TableId(type = IdType.AUTO)
    @Schema(description = "收藏记录ID")
    private Long id;

    /** 用户ID */
    @Schema(description = "用户ID")
    private Long userId;

    /** 菜品ID */
    @Schema(description = "菜品ID")
    private Long dishId;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "收藏时间")
    private LocalDateTime createdAt;
}
