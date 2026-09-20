package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品列表查询参数。
 * <p>
 * 已下线参数（2026-09-20 拍板）：{@code tag}（标签整链删除）、
 * {@code spiceLevel}（辣度维度删除，学生端不再提供辣度筛选入口）。
 */
@Data
@Schema(description = "菜品列表查询参数")
public class DishQueryReq {

    @Schema(description = "页码，从1开始", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "关键词，匹配菜品名/别名或档口名/食堂名", example = "牛肉")
    private String keyword;

    @Schema(description = "食堂ID筛选", example = "1")
    private Long canteenId;

    @Schema(description = "档口ID筛选", example = "1")
    private Long stallId;

    @Schema(description = "最低价格，单位：分", example = "1000")
    private Integer minPrice;

    @Schema(description = "最高价格，单位：分", example = "2000")
    private Integer maxPrice;

    @Schema(description = "排序字段：heat（热度）、rating、price、created_at", example = "heat")
    private String sortBy;

    @Schema(description = "排序方向：asc、desc", example = "desc")
    private String sortOrder;
}
