package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品列表查询参数 DTO
 * <p>
 * GET /api/dishes 的查询参数封装
 * 支持关键词搜索、食堂/档口筛选、标签筛选、价格区间、排序
 */
@Data
@Schema(description = "菜品列表查询参数")
public class DishQueryReq {

    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "关键词搜索（菜品名模糊匹配）", example = "牛肉")
    private String keyword;

    @Schema(description = "食堂ID筛选")
    private Long canteenId;

    @Schema(description = "档口ID筛选")
    private Long stallId;

    @Schema(description = "标签筛选（recommended=必吃, signature=招牌）", example = "recommended")
    private String tag;

    @Schema(description = "最低价格（分）")
    private Integer minPrice;

    @Schema(description = "最高价格（分）")
    private Integer maxPrice;

    @Schema(description = "排序字段（rating/collects/price/created_at）", example = "rating")
    private String sortBy;

    @Schema(description = "排序方向（asc/desc）", example = "desc")
    private String sortOrder;
}
