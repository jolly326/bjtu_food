package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "菜品列表查询参数")
public class DishQueryReq {

    @Schema(description = "页码，从1开始", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "关键词，匹配菜品名或档口名", example = "牛肉")
    private String keyword;

    @Schema(description = "食堂ID筛选", example = "1")
    private Long canteenId;

    @Schema(description = "档口ID筛选", example = "1")
    private Long stallId;

    @Schema(description = "品类ID筛选（category.id，首页品类滚轮使用）", example = "1")
    private Long categoryId;

    @Schema(description = "标签筛选，例如 recommended、signature、halal、western", example = "recommended")
    private String tag;

    @Schema(description = "口味（辣度）筛选枚举：0=不辣 1=微辣 2=中辣 3=重辣（find 口味 Sheet 使用）", example = "2")
    private Integer spiceLevel;

    @Schema(description = "最低价格，单位：分", example = "1000")
    private Integer minPrice;

    @Schema(description = "最高价格，单位：分", example = "2000")
    private Integer maxPrice;

    @Schema(description = "排序字段：heat（热度）、rating、price、created_at（collects 已随收藏模块移除）", example = "heat")
    private String sortBy;

    @Schema(description = "排序方向：asc、desc", example = "desc")
    private String sortOrder;

    @Schema(description = "排除的菜品ID集合（推荐接口去重前端已展示项，DB 侧下推）", example = "[1,2]")
    private List<Long> excludeIds;
}
