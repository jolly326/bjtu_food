package com.bjtufood.content.category.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜品分类实体类
 * <p>
 * 对应数据库表：category
 * find 页分类宫格的数据来源；图标由前端按 docs/mini-app-ui.md §0.5 映射表落 SVG，后端不出图标二进制。
 */
@Data
@TableName("category")
@Schema(description = "菜品分类")
public class Category {

    @TableId(type = IdType.AUTO)
    @Schema(description = "分类ID")
    private Long id;

    @Schema(description = "分类名称", example = "午餐")
    private String name;

    @Schema(description = "排序权重（越小越靠前，对应 find 宫格顺序）")
    private Integer sortOrder;

    @Schema(description = "状态：enabled/disabled", example = "enabled")
    private String status;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
