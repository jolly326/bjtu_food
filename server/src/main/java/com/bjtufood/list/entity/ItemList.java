package com.bjtufood.list.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 美食清单实体类
 * <p>
 * 对应数据库表：item_list
 * 用户创建的菜品收藏清单，可分享给微信好友
 */
@Data
@TableName("item_list")
@Schema(description = "美食清单")
public class ItemList {

    @TableId(type = IdType.AUTO)
    @Schema(description = "清单ID")
    private Long id;

    /** 创建者用户ID */
    @Schema(description = "创建者用户ID")
    private Long userId;

    /** 清单名称（如"一食堂必吃TOP3"） */
    @Schema(description = "清单名称", example = "一食堂必吃TOP3")
    private String name;

    /** 清单描述 */
    @Schema(description = "清单描述")
    private String description;

    /** 分享 token（创建时自动生成 UUID） */
    @Schema(description = "分享token")
    private String shareToken;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
