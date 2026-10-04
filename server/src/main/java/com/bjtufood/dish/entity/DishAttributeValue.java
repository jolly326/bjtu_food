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
 * 菜品描述属性**取值**（取值字典，表 {@code dish_attribute_value}；A4 落地 2026-10-03）。
 * <p>
 * <b>模型反转</b>：取值有独立行与 ID；`dish.attributes` 存的是**取值 ID**
 * （`single` 为数字 / `multi` 为数字数组），出参经服务端翻译为中文 ⇒ 客户端展示契约不变。
 * 因此改 `label` 只改一行、**全站生效（改名免费）**。
 * <p>
 * 删除约束：被菜品引用时禁止删除（应用层统计 `dishCount`），避免菜品属性读到悬空 ID。
 * 口径真源：docs/schema/dish_attribute_value.md 与 docs/api/web/dimensions.md。
 */
@Data
@TableName("dish_attribute_value")
@Schema(description = "菜品描述属性取值（取值字典）")
public class DishAttributeValue {

    @TableId(type = IdType.AUTO)
    @Schema(description = "取值ID（菜品 attributes 引用的就是它）")
    private Long id;

    @Schema(description = "所属维度ID")
    private Long dimensionId;

    @Schema(description = "取值中文名（展示用；可改，改名免费）", example = "半荤")
    private String label;

    /** MySQL 保留字列，列名显式加反引号（MyBatis-Plus 不自动转义保留字） */
    @TableField("`order`")
    @Schema(description = "组内展示顺序（升序）", example = "1")
    private Integer order;

    /** 更新时间（管理端列表出参；本表**无 created_at**，2026-10-02 统一口径） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
