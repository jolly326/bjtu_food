package com.bjtufood.dish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜品种类字典（表 {@code dish_category}）。
 * <p>
 * 本表是菜品「种类」的**唯一真源**：{@code dish.category_id} 与
 * {@code dish_filter_view.conditions} 的 {@code categoryId} 引用的都是本表 {@code id}。
 * 种类与菜品是 **1:1**（互斥、必填、单值列 + 索引）。
 * <p>
 * 与描述属性（{@code dish_attribute_dimension} / {@code dish_attribute_value}）的关系：
 * 后者与菜品是 **m:n**（可多值、可空、存 {@code dish.attributes} JSON）——
 * 两者是两种不同关系、两个独立实体，不共用表、不共用端点。
 * <p>
 * 数据锚在 {@code id}：改 {@code label} 只改一行、**全站生效**，零菜品迁移。
 * 删除约束：被菜品引用时禁止删除（应用层统计 {@code dishCount}），
 * 避免菜品读到悬空 ID；本字典**无删除端点**。
 * <p>
 * 口径真源：docs/schema/dish_category.md 与 docs/api/web/categories.md。
 */
@Data
@TableName("dish_category")
@Schema(description = "菜品种类字典")
public class DishCategory {

    @TableId(type = IdType.AUTO)
    @Schema(description = "种类ID（dish.category_id 与视图条件 categoryId 引用的就是它）")
    private Long id;

    @Schema(description = "种类中文名（展示用；可改，改名免费）", example = "面食粉类")
    private String label;

    /** 展示顺序（升序；管理端拖拽维护） */
    @Schema(description = "展示顺序（升序）", example = "1")
    private Integer sortOrder;

    /** 更新时间（管理端列表出参；本表**无 created_at**；DB 时钟 `ON UPDATE CURRENT_TIMESTAMP`，应用层不写、不填充） */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
