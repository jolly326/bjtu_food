package com.bjtufood.dish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品属性维度（动态属性模型两表之一，表 {@code dish_attribute_dimension}）。
 * <p>
 * 维度 ID 是唯一锚点：菜品 {@code dish.attributes} JSON 的键 = **描述维度** {@code id}
 * （JSON 内为字符串形态的十进制 ID）—— 后端据此拼装详情展示项与编辑候选值，
 * 消费方 SHALL NOT 另建映射。
 * 维度 {@code name} 只是展示文案，改名只改一行、全站生效。
 * 新增描述维度 = 插一行（{@code system = 0}），**免 ALTER、免发版**。
 * <p>
 * <b>系统维度</b>（{@code system = 1}，至多一行，当前 = 「菜品种类」）：取值是**菜品种类字典**，
 * 经 {@code dish.meal_type_id} 引用，**不写入 {@code attributes}**；不可删、不可改 {@code valueType}。
 */
@Data
@TableName("dish_attribute_dimension")
@Schema(description = "菜品属性维度")
public class DishAttributeDimension {

    @TableId(type = IdType.AUTO)
    @Schema(description = "维度ID（= 菜品 attributes 的键）")
    private Long id;

    @Schema(description = "维度中文名（饮食属性 / 食材 / 口味 / 冷热）", example = "饮食属性")
    private String name;

    /** 取值类型：single=单值 / multi=多值（系统维度恒为 single，不可改） */
    @Schema(description = "取值类型：single=单值 / multi=多值", example = "single")
    private String valueType;

    /**
     * 是否**系统维度**（1 = 系统维度，至多一行为 1）：
     * 取值经 {@code dish.meal_type_id} 引用、不写入 {@code attributes}；维度不可删、取值类型不可改。
     * 应用层不提供系统维度的新增 / 删除入口，故该列只会被建库维护写入。
     */
    @TableField("`system`")
    @Schema(description = "是否系统维度（1 = 菜品种类：不可删、取值类型不可改、取值不写入 attributes）")
    private Boolean system;

    /** 展示顺序（升序） */
    @Schema(description = "维度展示顺序（升序）", example = "1")
    private Integer sortOrder;

    /** 更新时间（管理端列表出参 `updatedAt`；DB 时钟 `ON UPDATE CURRENT_TIMESTAMP`，应用层不写、不填充） */
    @Schema(description = "更新时间")
    private java.time.LocalDateTime updatedAt;
}
