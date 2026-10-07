package com.bjtufood.dish.view;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 首页筛选视图项（{@code GET /dishes/views} 出参）。
 * <p>
 * 顺序由服务端下发次序表达，端上按数组顺序渲染、不读序号字段。
 * 标签文案是<b>服务端资产</b>（含「为你推荐」等虚拟导航视图），端上零硬编码。
 * <p>
 * 出参恰 {@code id} / {@code label} 两项：{@code id} 供端上回传（{@code GET /dishes?view=<id>}），
 * {@code label} 供端上直接渲染；筛选条件与排序口径都存服务端（见 {@code dish_filter_view}），端上不感知。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "首页筛选视图项")
public class DishViewVO {

    @Schema(description = "视图ID（端上回传 view=<id> 用于筛选）", example = "2")
    private Long id;

    @Schema(description = "中文标签（端上直接渲染）", example = "面食粉类")
    private String label;
}
