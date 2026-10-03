package com.bjtufood.dashboard.controller.admin;

import com.bjtufood.auth.dto.UserOverviewVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.result.AdminPageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.correction.dto.DishCorrectionAdminVO;
import com.bjtufood.correction.service.CorrectionService;
import com.bjtufood.dashboard.dto.DashboardVO;
import com.bjtufood.dish.dto.DishHealthVO;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.service.FeedbackService;
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
 * D1 运营看板（管理端，**只读聚合**）。
 *
 * <p>契约真源：docs/web/D-运营/D1-运营看板.md。定位：**打开后台即见的首屏** ——
 * 把「今天要处理什么」与「主数据有没有该修的」一屏看完、**点击直达**处置页。
 *
 * <p><b>为什么本控制器是唯一跨域处</b>：分层护栏放行 `*.controller` 跨域（controller = **编排层**，
 * 只经各域 Service 契约），业务层之间不出现反向依赖。故聚合逻辑落在本层，各域只提供自己的计数 / 列表。
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
    /** 各域各取若干条后再归并（跨两表，量级极小） */
    private static final int PER_SOURCE_LIMIT = RECENT_LIMIT;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FeedbackService feedbackService;
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

    /** ① 待办：三类计数（复用各自列表端点的分页壳，口径与列表页**同一判据**）+ 最近 5 条 */
    private DashboardVO.Todo buildTodo() {
        DashboardVO.Todo todo = new DashboardVO.Todo();
        todo.setPendingFeedbackCount(pendingFeedback());
        todo.setPendingReportCount(pendingReport());
        todo.setPendingCorrectionCount(pendingCorrection());

        List<DashboardVO.RecentTodo> recent = new ArrayList<>();
        // 反馈与举报共用同一列表端点（category 分流），两类的「待办」在 D1 是两行统计、一个合并列表
        recent.addAll(feedbackRecent("feedback"));
        recent.addAll(feedbackRecent("report"));
        recent.addAll(correctionRecent());
        recent.sort(Comparator.comparing(DashboardVO.RecentTodo::getSubmittedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        todo.setRecent(recent.stream().limit(RECENT_LIMIT).toList());
        return todo;
    }

    private long pendingFeedback() {
        return feedbackService.listForAdmin("feedback", "pending", null, null, null, 1, 1).getTotal();
    }

    private long pendingReport() {
        return feedbackService.listForAdmin("report", "pending", null, null, null, 1, 1).getTotal();
    }

    private long pendingCorrection() {
        return correctionService.listForAdmin("pending", null, 1, 1).getTotal();
    }

    /** 反馈 / 举报的最近待办（摘要取正文，超长截断 —— 看板只做「一眼看出是什么」。 */
    private List<DashboardVO.RecentTodo> feedbackRecent(String category) {
        AdminPageResult<FeedbackAdminVO> page =
                AdminPageResult.of(feedbackService.listForAdmin(category, "pending", null, null, null, 1, PER_SOURCE_LIMIT));
        List<DashboardVO.RecentTodo> items = new ArrayList<>(page.getRecords().size());
        for (FeedbackAdminVO row : page.getRecords()) {
            DashboardVO.RecentTodo item = new DashboardVO.RecentTodo();
            item.setKind(category);
            item.setId(row.getId());
            item.setTitle(abbreviate(row.getContent()));
            item.setSubmittedAt(format(row.getCreatedAt()));
            items.add(item);
        }
        return items;
    }

    /** 纠错的最近待办（摘要取「目标菜品名」——纠错的正文是结构化改动项，菜名最能说明是哪一条） */
    private List<DashboardVO.RecentTodo> correctionRecent() {
        AdminPageResult<DishCorrectionAdminVO> page =
                AdminPageResult.of(correctionService.listForAdmin("pending", null, 1, PER_SOURCE_LIMIT));
        List<DashboardVO.RecentTodo> items = new ArrayList<>(page.getRecords().size());
        for (DishCorrectionAdminVO row : page.getRecords()) {
            DashboardVO.RecentTodo item = new DashboardVO.RecentTodo();
            item.setKind("correction");
            item.setId(row.getId());
            item.setTitle(row.getDishName() == null ? "菜品已删除" : row.getDishName());
            item.setSubmittedAt(format(row.getCreatedAt()));
            items.add(item);
        }
        return items;
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
        return trimmed.length() <= 30 ? trimmed : trimmed.substring(0, 30) + "…";
    }

    private static String format(java.time.LocalDateTime time) {
        return time == null ? null : TIME.format(time);
    }
}
