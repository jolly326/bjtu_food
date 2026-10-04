package com.bjtufood.dish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜品分类值字典（表 {@code dish_category_value}；A6 落地 2026-10-03）。
 * <p>
 * `dish.meal_type` 的**取值域**：列定义不变（VARCHAR(20) 存 `key`），本表把值域从代码常量
 * （原视图常量的 mealType 取值）搬进库。
 * <ul>
 *   <li>{@code key}：数据锚点（`dish.meal_type` 存的就是它）⇒ **在用后不可改**；</li>
 *   <li>{@code label}：中文名，**改名免费**（改一行、全站生效）；</li>
 *   <li>删除约束：被菜品引用时禁止删除（`dishCount > 0`）；清理同义值走**合并**。</li>
 * </ul>
 * 口径真源：docs/schema/dish_category_value.md 与 docs/api/web/categories.md。
 */
@Data
@TableName("dish_category_value")
@Schema(description = "菜品分类值（分类值字典）")
public class DishCategoryValue {

    @TableId(type = IdType.AUTO)
    @Schema(description = "分类ID")
    private Long id;

    @Schema(description = "分类键（dish.meal_type 存的就是它；在用后不可改）", example = "noodle")
    private String key;

    @Schema(description = "分类中文名（可改，改名免费）", example = "面食粉类")
    private String label;

    /** MySQL 保留字列，列名显式加反引号（MyBatis-Plus 不自动转义保留字） */
    @TableField("`order`")
    @Schema(description = "顺序（后台下拉 / 列表展示序）", example = "3")
    private Integer order;

    /** 更新时间（本表**无 created_at**，与 dish_attribute_* 同口径） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
