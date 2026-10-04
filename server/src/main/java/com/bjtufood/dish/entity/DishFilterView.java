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
 * 首页筛选视图（表 {@code dish_filter_view}）—— 只存 tab 的**展示态**。
 * <p>
 * 一个 tab = `key` + 文案 + 顺序 + 启停（**本表**）；筛选标准 + 排序口径是**固定逻辑**，
 * 按 `key` 存放于代码常量 {@code DishViewDefs}（**不进库**）。
 * <ul>
 *   <li>{@code key}：端上回传 {@code view=<key>} ⇒ **在用后不可改**，且须与 {@code DishViewDefs} 的键一致；</li>
 *   <li>后台只维护文案 / 顺序 / 启停；无「默认视图」概念（空 `view` 取首个启用视图）。</li>
 * </ul>
 * 口径真源：docs/schema/dish_filter_view.md 与 docs/api/web/views.md。
 */
@Data
@TableName("dish_filter_view")
@Schema(description = "首页筛选视图")
public class DishFilterView {

    @TableId(type = IdType.AUTO)
    @Schema(description = "视图ID")
    private Long id;

    @TableField("`key`")
    @Schema(description = "视图键（端上回传 view=<key>；在用后不可改）", example = "noodle")
    private String key;

    @Schema(description = "tab 文案（可改）", example = "面食粉类")
    private String label;

    @TableField("`order`")
    @Schema(description = "展示顺序（升序）", example = "4")
    private Integer order;

    @Schema(description = "是否在 client 首页出现（false=tab 隐藏）")
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
