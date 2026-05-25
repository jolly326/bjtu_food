package com.bjtufood.canteen.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

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
