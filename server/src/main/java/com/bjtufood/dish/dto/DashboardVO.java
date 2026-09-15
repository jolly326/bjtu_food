package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 工作台视图对象（Web 后台 dashboard）
 * <p>
 * 2026-09-14 用户拍板（Q-106）：工作台不含图表看板，前端不消费热度排行/趋势字段，
 * 故移除 hotCanteens / hotDishes / viewTrend / reviewTrend 及其全表菜品聚合查询。
 * 仅保留「待办 + 规模指标 + 近期操作」。
 */
@Data
@Schema(description = "数据看板")
public class DashboardVO {

    @Schema(description = "时间范围（start~end）")
    private String range;

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

    @Schema(description = "总反馈数")
    private Long totalFeedbackCount;

    @Schema(description = "待处理反馈数")
    private Long pendingFeedbackCount;

    @Schema(description = "待处理反馈明细（最近 5 条）")
    private List<TodoItem> pendingFeedbacks;

    @Schema(description = "近期操作（操作日志最近 10 条）")
    private List<RecentLogItem> recentLogs;

    @Data
    @Schema(description = "待办明细项")
    public static class TodoItem {
        @Schema(description = "ID")
        private Long id;
        @Schema(description = "标题（内容摘要）")
        private String title;
        @Schema(description = "类型（反馈类型）")
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
