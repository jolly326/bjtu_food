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
 * 菜品分类值字典（表 {@code dish_category_value}；A6 落地）。
 * <p>
 * `dish.meal_type` 的**取值域**（存本表 `id`）。
 * <ul>
 *   <li>{@code id}：数据锚点（`dish.meal_type` 存的就是它）⇒ 改 `label` 免费；</li>
 *   <li>{@code key}：**代码锚点**（内置视图常量按 `key` 引用分类；新建时选填、缺省由后端自动生成）；</li>
 *   <li>删除约束：被菜品引用时禁止删除（`dishCount > 0`）。</li>
 * </ul>
 * 口径真源：docs/schema/dish_category_value.md 与 docs/api/web/categories.md。
 */
@Data
@TableName("dish_category_value")
@Schema(description = "菜品分类值（分类值字典）")
public class DishCategoryValue {

    @TableId(type = IdType.AUTO)
    @Schema(description = "分类ID（dish.meal_type 存的就是它）")
    private Long id;

    @TableField("`key`")
    @Schema(description = "分类键（代码锚点，视图条件引用它；新建时选填、缺省由后端自动生成；在用后不可改）", example = "noodle")
    private String key;

    @Schema(description = "分类中文名（可改，改名免费）", example = "面食粉类")
    private String label;

    /** 顺序（后台下拉 / 列表展示序） */
    @Schema(description = "顺序（后台下拉 / 列表展示序）", example = "3")
    private Integer sortOrder;

    /** 更新时间（本表**无 created_at**，与 dish_attribute_* 同口径） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
