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

    @Schema(description = "总食堂数")
    private Long totalCanteenCount;

    @Schema(description = "总档口数")
    private Long totalStallCount;

    @Schema(description = "总学生数")
    private Long totalUserCount;

    @Schema(description = "总动态数")
    private Long totalMomentCount;

    @Schema(description = "总申请数")
    private Long totalApplyCount;

    @Schema(description = "总反馈数")
    private Long totalFeedbackCount;

    @Schema(description = "待审核申请数")
    private Long pendingApplyCount;

    @Schema(description = "待审核动态数")
    private Long pendingMomentCount;

    @Schema(description = "待处理反馈数")
    private Long pendingFeedbackCount;

    @Schema(description = "待审核申请明细（最近 5 条）")
    private List<TodoItem> pendingApplies;

    @Schema(description = "待审核动态明细（最近 5 条）")
    private List<TodoItem> pendingMoments;

    @Schema(description = "待处理反馈明细（最近 5 条）")
    private List<TodoItem> pendingFeedbacks;

    @Schema(description = "近期操作（操作日志最近 10 条）")
    private List<RecentLogItem> recentLogs;

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

    @Data
    @Schema(description = "待办明细项")
    public static class TodoItem {
        @Schema(description = "ID")
        private Long id;
        @Schema(description = "标题（申请类型 / 内容摘要）")
        private String title;
        @Schema(description = "类型（entityType 或反馈类型）")
        private String type;
        @Schema(description = "提交时间（yyyy-MM-dd HH:mm）")
        private String time;
    }

    @Data
    @Schema(description = "近期操作项")
    public static class RecentLogItem {
        @Schema(description = "日志 ID")
        private Long id;
        @Schema(description = "操作人昵称")
        private String operator;
        @Schema(description = "动作")
        private String action;
        @Schema(description = "对象描述")
        private String target;
        @Schema(description = "时间（yyyy-MM-dd HH:mm）")
        private String time;
    }
}
