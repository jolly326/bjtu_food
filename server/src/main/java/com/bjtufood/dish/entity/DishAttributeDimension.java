package com.bjtufood.dish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品描述属性维度（动态属性模型两表之一，表 {@code dish_attribute_dimension}）。
 * <p>
 * 维度键 {@code fieldKey} 恒等于菜品 {@code dish.attributes} JSON 的键（camelCase：
 * {@code dietType} / {@code ingredients} / {@code flavorTags} / {@code serveTemp}）——
 * 后端据此拼装详情展示项与编辑候选值，消费方 SHALL NOT 另建映射。
 * 新增维度 = 插一行，**免 ALTER、免发版**。
 */
@Data
@TableName("dish_attribute_dimension")
@Schema(description = "菜品描述属性维度")
public class DishAttributeDimension {

    @TableId(type = IdType.AUTO)
    @Schema(description = "维度ID")
    private Long id;

    @Schema(description = "维度键（= 菜品 attributes 的键，camelCase）", example = "dietType")
    private String fieldKey;

    @Schema(description = "维度中文名（饮食属性 / 食材 / 口味 / 冷热）", example = "饮食属性")
    private String name;

    /** 取值类型：single=单值 / multi=多值 */
    @Schema(description = "取值类型：single=单值 / multi=多值", example = "single")
    private String valueType;

    /** MySQL 保留字列，列名显式加反引号（MyBatis-Plus 不自动转义保留字） */
    @TableField("`order`")
    @Schema(description = "维度展示顺序（升序）", example = "1")
    private Integer order;
}
