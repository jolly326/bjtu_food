package com.bjtufood.dish.view;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 首页筛选视图项（{@code GET /dishes/views} 出参）。
 * <p>
 * 顺序由服务端下发次序表达（{@link DishViewConst#ALL} 声明序），端上按数组顺序渲染、不读序号字段。
 * 标签文案是<b>服务端资产</b>（含「为你推荐」等虚拟导航视图），端上零硬编码。
 * <p>
 * 出参恰 {@code key} / {@code label} 两项：{@code key} 供端上回传（{@code GET /dishes?view=<key>}），
 * {@code label} 供端上直接渲染。
 *
 * @see DishViewConst
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "首页筛选视图项")
public class DishViewVO {

    @Schema(description = "视图键（端上回传 view=<key> 用于筛选）", example = "noodle")
    private String key;

    @Schema(description = "中文标签（端上直接渲染）", example = "面食粉类")
    private String label;
}
