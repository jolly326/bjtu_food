package com.bjtufood.list.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 清单项实体类
 * <p>
 * 对应数据库表：list_item
 * 美食清单与菜品的多对多关联表，一条记录表示"某清单中包含某菜品"
 */
@Data
@TableName("list_item")
@Schema(description = "清单项")
public class ListItem {

    @TableId(type = IdType.AUTO)
    @Schema(description = "清单项ID")
    private Long id;

    /** 所属清单ID */
    @Schema(description = "所属清单ID")
    private Long listId;

    /** 菜品ID */
    @Schema(description = "菜品ID")
    private Long dishId;
}
