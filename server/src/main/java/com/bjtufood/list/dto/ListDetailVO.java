package com.bjtufood.list.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 清单详情视图对象
 * <p>
 * 展示清单及其包含的所有菜品完整信息
 * 用于 GET /api/lists/{id} 和 GET /api/lists/share/{token}
 */
@Data
@Schema(description = "清单详情信息")
public class ListDetailVO {

    @Schema(description = "清单ID")
    private Long id;

    @Schema(description = "清单名称", example = "一食堂必吃TOP3")
    private String name;

    @Schema(description = "清单描述")
    private String description;

    @Schema(description = "分享token")
    private String shareToken;

    @Schema(description = "包含的菜品列表")
    private List<DishItem> dishes;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /**
     * 清单内菜品摘要信息
     */
    @Data
    @Schema(description = "清单内菜品摘要")
    public static class DishItem {
        @Schema(description = "菜品ID")
        private Long id;

        @Schema(description = "菜品名称", example = "牛肉拉面")
        private String name;

        @Schema(description = "价格（分）", example = "1200")
        private Integer price;

        @Schema(description = "菜品图片URL列表")
        private List<String> images;

        @Schema(description = "平均评分", example = "4.5")
        private BigDecimal avgRating;

        @Schema(description = "所属档口名称", example = "面食窗口")
        private String stallName;
    }
}
