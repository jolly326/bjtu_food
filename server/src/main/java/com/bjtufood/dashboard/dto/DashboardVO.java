package com.bjtufood.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * D1 运营看板出参（**只读聚合**，一次请求返回全量 —— 首屏不该为三个分区发三次请求）。
 *
 * <p>契约真源：docs/web/D-运营/D1-运营看板.md。三区分区、**每区 ≤4 项**，且待办区**必须含可点击的最近列表**
 * （只有数字的话，看板会退化成「数字墙」，管理员仍得逐个菜单去翻）。
 *
 * <p><b>本页不写任何表</b>；计数口径与各自列表页同一判据（在售 = `status='on'`、待办 = `status='pending'`）。
 * 计数是**瞬时快照**，与列表页可能存在秒级偏差（可接受：点进去以列表为准）。
 */
@Data
@Schema(description = "运营看板（只读聚合）")
public class DashboardVO {

    @Schema(description = "① 待办区")
    private Todo todo;

    @Schema(description = "② 主数据健康度区")
    private Health health;

    @Schema(description = "③ 概况区（只读）")
    private Overview overview;

    /** ① 待办：有多少要处理 + **最近的是哪些**（可点击直达）。 */
    @Data
    @Schema(description = "待办区")
    public static class Todo {

        @Schema(description = "待处理意见反馈数（非举报且 status='pending'）")
        private Long pendingFeedbackCount;

        @Schema(description = "待处理举报数（type='report' 且 status='pending'）")
        private Long pendingReportCount;

        @Schema(description = "待处理纠错数（dish_correction：status='pending'）")
        private Long pendingCorrectionCount;

        @Schema(description = "最近 5 条待办（跨三类合并，按提交时间倒序）")
        private List<RecentTodo> recent;
    }

    /** `recent[]` 单项：`kind` 决定跳哪个处置页。 */
    @Data
    @Schema(description = "最近待办项")
    public static class RecentTodo {

        @Schema(description = "类别：feedback / report / correction（决定跳转哪个处置页）", example = "feedback")
        private String kind;

        @Schema(description = "对应记录 ID（跳转定位）", example = "12")
        private Long id;

        @Schema(description = "摘要：反馈=正文摘要；举报=被举报评价摘要；纠错=菜品名")
        private String title;

        @Schema(description = "提交时间（yyyy-MM-dd HH:mm:ss）")
        private String submittedAt;
    }

    /**
     * ② 主数据健康度：只收**管理员当场能修**的项（不列「描述为空」这类允许为空的噪音项）。
     *
     * <p>口径：三个菜品项统计**全部菜品（含下架）** —— 健康度是**存量清理指标**，
     * 下架菜品的缺失同样要修；「空档口」按档口全量统计。
     */
    @Data
    @Schema(description = "主数据健康度区")
    public static class Health {

        @Schema(description = "无图片的菜品数（client 卡片只能走占位图）")
        private Long dishesWithoutImage;

        @Schema(description = "未归入档口的菜品数（stall_id = 0）")
        private Long dishesWithoutStall;

        @Schema(description = "分类为空的菜品数（meal_type 为空）")
        private Long dishesWithoutCategory;

        @Schema(description = "无菜品的档口数（空档口）")
        private Long stallsWithoutDish;
    }

    /** ③ 概况：只给规模、不给时间序列（趋势图属「运营系统」范畴，当前不做）。 */
    @Data
    @Schema(description = "概况区")
    public static class Overview {

        @Schema(description = "用户总数（不含已注销）")
        private Long userCount;

        @Schema(description = "已认证用户数（bind_email 非空）")
        private Long verifiedUserCount;

        @Schema(description = "在售菜品数（status='on'）")
        private Long onSaleDishCount;

        @Schema(description = "评价总数（含已隐藏）")
        private Long reviewCount;
    }
}
