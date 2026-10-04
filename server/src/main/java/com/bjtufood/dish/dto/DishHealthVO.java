package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品健康度计数（D1 看板用；**跨域聚合由 dashboard.controller 编排**，故只出本域的 4 个计数）。
 *
 * <p>口径：`onSaleCount` = `status='on'`；三个「缺失」项统计**全部菜品（含下架）** ——
 * 健康度是存量清理指标，下架菜品的缺失同样要修（见 docs/func/web/D-运营/D1-运营看板.md）。
 */
@Data
@Schema(description = "菜品健康度计数")
public class DishHealthVO {

    @Schema(description = "在售菜品数（status='on'）")
    private Long onSaleCount;

    @Schema(description = "无图片的菜品数")
    private Long withoutImage;

    @Schema(description = "未归入档口的菜品数（stall_id = 0）")
    private Long withoutStall;

    @Schema(description = "分类为空的菜品数（meal_type 为空）")
    private Long withoutCategory;
}
