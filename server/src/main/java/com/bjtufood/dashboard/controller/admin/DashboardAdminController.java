package com.bjtufood.dashboard.controller.admin;

import com.bjtufood.auth.dto.UserOverviewVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.result.AdminPageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.correction.dto.CorrectionAdminVO;
import com.bjtufood.correction.service.CorrectionService;
import com.bjtufood.dashboard.dto.DashboardVO;
import com.bjtufood.dish.dto.DishHealthVO;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.dto.ReportAdminVO;
import com.bjtufood.feedback.service.FeedbackService;
import com.bjtufood.feedback.service.ReportService;
import com.bjtufood.moderation.constant.ModerationConst;
import com.bjtufood.moderation.service.ModerationTaskService;
import com.bjtufood.review.service.ReviewService;
import com.bjtufood.canteen.service.StallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 运营看板（管理端，**只读聚合**）。
 *
 * <p>契约真源：docs/api/web/dashboard.md。定位：**打开后台即见的首屏** ——
 * 把「今天要处理什么」与「主数据有没有该修的」一屏看完、**点击直达**处置页。
 *
 * <p><b>为什么本控制器是唯一跨域处</b>：分层护栏放行 `*.controller` 跨域（controller = **编排层**，
 * 只经各域 Service 契约），业务层之间不出现反向依赖。故聚合逻辑落在本层，各域只提供自己的计数 / 列表。
 *
 * <p><b>待办计数口径</b>：三类待处置数一律取自处置任务表（`moderation_task`）**单表 COUNT**
 * ——「是否待处置」是任务行的状态，三类工单共用同一判据，故不存在「列表页与看板口径不一致」的可能。
 *
 * <p><b>不缓存</b>（首屏要准，单管理员访问频率极低）、**不写任何表**、**不做时间序列**
 * （趋势图属「运营系统」范畴，当前单管理员轻运营不做）。
 */
@Tag(name = "02. 后台运营看板", description = "登录后首屏的统一待办入口：待办（含最近 5 条可点击）/ 主数据健康度 / 概况。"
        + "只读聚合，一次请求返回全量；不含操作日志。需要管理员 token。")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class DashboardAdminController {

    /** 最近待办条数（看板的核心价值：数字之外还得能直接点进去） */
    private static final int RECENT_LIMIT = 5;
    /** 摘要截断长度（看板一行放不下长正文） */
    private static final int ABBREV_MAX_LEN = 30;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ModerationTaskService moderationTaskService;
    private final FeedbackService feedbackService;
    private final ReportService reportService;
    private final CorrectionService correctionService;
    private final DishService dishService;
    private final StallService stallService;
    private final ReviewService reviewService;
    private final UserService userService;

    @Operation(summary = "运营看板", description = "用途：登录后首屏（**只读、无参数、不分页**，一次返回全量）。"
            + "待办区含跨三类合并的最近 5 条待办（可点击直达处置页）；健康度只收管理员当场能修的项；概况只给规模。")
    @GetMapping
    public Result<DashboardVO> dashboard() {
        DashboardVO vo = new DashboardVO();
        vo.setTodo(buildTodo());
        vo.setHealth(buildHealth());
        vo.setOverview(buildOverview());
        return Result.success(vo);
    }

    /** ① 待办：三类计数（处置任务表单表 COUNT）+ 最近 5 条（各域取本域待处置列表的首页） */
    private DashboardVO.Todo buildTodo() {
        DashboardVO.Todo todo = new DashboardVO.Todo();
        todo.setPendingFeedbackCount(moderationTaskService.countPending(ModerationConst.TYPE_FEEDBACK));
        todo.setPendingReportCount(moderationTaskService.countPending(ModerationConst.TYPE_REPORT));
        todo.setPendingCorrectionCount(moderationTaskService.countPending(ModerationConst.TYPE_CORRECTION));

        List<DashboardVO.RecentTodo> recent = new ArrayList<>();
        recent.addAll(feedbackRecent());
        recent.addAll(reportRecent());
        recent.addAll(correctionRecent());
        recent.sort(Comparator.comparing(DashboardVO.RecentTodo::getSubmittedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        todo.setRecent(recent.stream().limit(RECENT_LIMIT).toList());
        return todo;
    }

    /** 反馈 / 举报 / 纠错的最近待办（摘要取各自最能说明「是哪一条」的字段，超长截断） */
    private List<DashboardVO.RecentTodo> feedbackRecent() {
        AdminPageResult<FeedbackAdminVO> page = AdminPageResult.of(
                feedbackService.listForAdmin(ModerationConst.STATUS_PENDING, null, null, null, 1, RECENT_LIMIT));
        List<DashboardVO.RecentTodo> items = new ArrayList<>(page.getRecords().size());
        for (FeedbackAdminVO row : page.getRecords()) {
            items.add(recentTodo("feedback", row.getId(), abbreviate(row.getContent()), row.getCreatedAt()));
        }
        return items;
    }

    /**
     * 举报的最近待办（摘要取被举报评价正文 —— 举报无正文，被举报了什么才是「是哪一条」的答案）。
     * <p>被举报评价已删除（{@code reviewId} 为空）时给固定文案，避免看板出现空白行。
     */
    private List<DashboardVO.RecentTodo> reportRecent() {
        AdminPageResult<ReportAdminVO> page = AdminPageResult.of(
                reportService.listForAdmin(ModerationConst.STATUS_PENDING, null, 1, RECENT_LIMIT));
        List<DashboardVO.RecentTodo> items = new ArrayList<>(page.getRecords().size());
        for (ReportAdminVO row : page.getRecords()) {
            String summary = Boolean.TRUE.equals(row.getReviewDeleted())
                    ? "被举报评价已删除" : abbreviate(row.getReviewContent());
            items.add(recentTodo("report", row.getId(), summary, row.getCreatedAt()));
        }
        return items;
    }

    /** 纠错的最近待办（摘要取「目标菜品名」——纠错的正文是结构化改动项，菜名最能说明是哪一条） */
    private List<DashboardVO.RecentTodo> correctionRecent() {
        AdminPageResult<CorrectionAdminVO> page = AdminPageResult.of(
                correctionService.listForAdmin(ModerationConst.STATUS_PENDING, null, null, 1, RECENT_LIMIT));
        List<DashboardVO.RecentTodo> items = new ArrayList<>(page.getRecords().size());
        for (CorrectionAdminVO row : page.getRecords()) {
            String summary = row.getDishName() == null ? "菜品已删除" : row.getDishName();
            items.add(recentTodo("correction", row.getId(), summary, row.getCreatedAt()));
        }
        return items;
    }

    private static DashboardVO.RecentTodo recentTodo(String kind, Long id, String title,
                                                     java.time.LocalDateTime createdAt) {
        DashboardVO.RecentTodo item = new DashboardVO.RecentTodo();
        item.setKind(kind);
        item.setId(id);
        item.setTitle(title);
        item.setSubmittedAt(format(createdAt));
        return item;
    }

    /** ② 主数据健康度：四个「当场能修」的计数（不列允许为空的项，避免噪音让整区失去意义） */
    private DashboardVO.Health buildHealth() {
        DishHealthVO dish = dishService.countHealth();
        DashboardVO.Health health = new DashboardVO.Health();
        health.setDishesWithoutImage(dish.getWithoutImage());
        health.setDishesWithoutStall(dish.getWithoutStall());
        health.setDishesWithoutCategory(dish.getWithoutCategory());
        health.setStallsWithoutDish(stallService.countWithoutDish());
        return health;
    }

    /** ③ 概况：规模（不含已注销用户；评价含已隐藏） */
    private DashboardVO.Overview buildOverview() {
        UserOverviewVO user = userService.countOverview();
        DishHealthVO dish = dishService.countHealth();
        DashboardVO.Overview overview = new DashboardVO.Overview();
        overview.setUserCount(user.getUserCount());
        overview.setVerifiedUserCount(user.getVerifiedUserCount());
        overview.setOnSaleDishCount(dish.getOnSaleCount());
        overview.setReviewCount(reviewService.countAll());
        return overview;
    }

    /** 摘要截断（看板一行放不下长正文；完整内容点进处置页看） */
    private static String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "（无正文）";
        }
        String trimmed = text.trim();
        return trimmed.length() <= ABBREV_MAX_LEN ? trimmed : trimmed.substring(0, ABBREV_MAX_LEN) + "…";
    }

    private static String format(java.time.LocalDateTime time) {
        return time == null ? null : TIME.format(time);
    }
}
