package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 数据看板视图对象（Web 后台 dashboard）
 */
@Data
@Schema(description = "数据看板")
public class DashboardVO {

    @Schema(description = "时间范围（start~end）")
    private String range;

    @Schema(description = "本周上新菜品数")
    private Long newDishCount;

    @Schema(description = "本周新增评价数")
    private Long newReviewCount;

    @Schema(description = "总菜品数")
    private Long totalDishCount;

    @Schema(description = "总评价数")
    private Long totalReviewCount;

    @Schema(description = "最热门食堂（按浏览量/评价数）")
    private List<RankItem> hotCanteens;

    @Schema(description = "最热门菜品（按浏览量/收藏量）")
    private List<RankItem> hotDishes;

    @Schema(description = "浏览量趋势（按天）")
    private TrendData viewTrend;

    @Schema(description = "评价量趋势（按天）")
    private TrendData reviewTrend;

    @Data
    @Schema(description = "排行项")
    public static class RankItem {
        @Schema(description = "ID")
        private Long id;
        @Schema(description = "名称")
        private String name;
        @Schema(description = "热度值（浏览量或收藏量）")
        private Long score;
    }

    @Data
    @Schema(description = "趋势数据")
    public static class TrendData {
        @Schema(description = "日期标签列表，如 [01-01, 01-02]")
        private List<String> dates;
        @Schema(description = "数值列表")
        private List<Long> values;
    }
}
