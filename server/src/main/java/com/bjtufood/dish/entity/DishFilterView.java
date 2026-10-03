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
 * 首页筛选视图（表 {@code dish_filter_view}；A6 落地 2026-10-03）。
 * <p>
 * 一个 tab = `key` + 文案 + 顺序 + 启停 + 默认标记 + **筛选标准**（{@code conditions}）+ 排序口径。
 * 视图**不分类型**：用不用某字段由该视图自己的条件决定（「所有 tab 平等」）。
 * <ul>
 *   <li>{@code key}：端上回传 {@code view=<key>} ⇒ **在用后不可改**；</li>
 *   <li>{@code conditions}：**有限语言**（字段白名单 + 操作符 + 值 的 AND 组合），JSON 原文存取；</li>
 *   <li>{@code isDefault}：全站恰一个（切换默认时先清旧值，同事务）；默认视图**不可停用、不可删除**。</li>
 * </ul>
 * 口径真源：docs/schema/dish_filter_view.md 与 docs/web/A-主数据维护/A6-首页筛选视图管理.md。
 */
@Data
@TableName("dish_filter_view")
@Schema(description = "首页筛选视图")
public class DishFilterView {

    @TableId(type = IdType.AUTO)
    @Schema(description = "视图ID")
    private Long id;

    @Schema(description = "视图键（端上回传 view=<key>；在用后不可改）", example = "noodle")
    private String key;

    @Schema(description = "tab 文案（可改）", example = "面食粉类")
    private String label;

    @TableField("`order`")
    @Schema(description = "展示顺序（升序；默认视图排首位）", example = "4")
    private Integer order;

    @Schema(description = "是否在 client 首页出现（false=tab 隐藏）")
    private Boolean enabled;

    @Schema(description = "默认视图（端上不带 view 的落点；全站恰一个）")
    @TableField("is_default")
    private Boolean isDefault;

    /** 筛选标准（JSON 数组原文：`[{field, op, value}]`；NULL/[] = 全部菜品） */
    @Schema(description = "筛选标准（字段白名单 + 操作符 + 值的 AND 组合）")
    private String conditions;

    @Schema(description = "排序口径白名单：heat/priceAsc/priceDesc/discountDesc/ratingDesc/newest/random", example = "heat")
    private String sortKind;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
