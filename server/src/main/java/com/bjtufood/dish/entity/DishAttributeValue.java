package com.bjtufood.dish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品描述属性取值（动态属性模型两表之一，表 {@code dish_attribute_value}）。
 * <p>
 * 机器值 {@code valueKey} 与中文标签 {@code label} 的唯一真源：后端据此把菜品
 * {@code attributes} 里的机器值翻译为中文一并下发（R4：端上零翻译），
 * 并为编辑态提供候选选项。
 */
@Data
@TableName("dish_attribute_value")
@Schema(description = "菜品描述属性取值")
public class DishAttributeValue {

    @TableId(type = IdType.AUTO)
    @Schema(description = "取值ID")
    private Long id;

    @Schema(description = "所属维度ID", example = "1")
    private Long dimensionId;

    @Schema(description = "机器值（如 spicy）", example = "spicy")
    private String valueKey;

    @Schema(description = "中文标签（如 辣）", example = "辣")
    private String label;

    /** MySQL 保留字列，列名显式加反引号（MyBatis-Plus 不自动转义保留字） */
    @TableField("`order`")
    @Schema(description = "组内展示顺序（升序）", example = "1")
    private Integer order;
}
