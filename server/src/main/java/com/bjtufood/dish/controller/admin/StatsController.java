package com.bjtufood.dish.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.apply.entity.ApplyAction;
import com.bjtufood.apply.mapper.ApplyActionMapper;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.common.entity.OperationLog;
import com.bjtufood.common.mapper.OperationLogMapper;
import com.bjtufood.common.result.Result;
import com.bjtufood.dish.constant.DishConst;
import com.bjtufood.dish.dto.DashboardVO;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.moment.entity.Moment;
import com.bjtufood.moment.mapper.MomentMapper;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.mapper.ReviewMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据统计控制器（Web 后台数据看板）
 * <p>
 * 提供总览指标、最热门食堂/菜品排行、浏览量与评价量趋势（ECharts）。
 */
@Tag(name = "数据统计（数据看板）", description = "运营数据一览：上新/评价指标、热门排行、趋势图")
@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
public class StatsController {

    private final DishMapper dishMapper;
    private final ReviewMapper reviewMapper;
    private final CanteenMapper canteenMapper;
    private final StallMapper stallMapper;
    private final UserMapper userMapper;
    private final MomentMapper momentMapper;
    private final ApplyActionMapper applyActionMapper;
    private final FeedbackMapper feedbackMapper;
    private final OperationLogMapper operationLogMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Operation(summary = "数据看板总览", description = "用途：运营数据一览。支持 range=7/30（天），默认 7 天。返回本周上新、本周评价、热门排行与趋势。")
    @GetMapping("/overview")
    public Result<DashboardVO> overview(@RequestParam(defaultValue = "7") int range) {
        if (range != 7 && range != 30) range = 7;
        LocalDateTime since = LocalDate.now().minusDays(range).atStartOfDay();
        DashboardVO vo = new DashboardVO();
        vo.setRange("近" + range + "天");

        // 主体统计：整体容错，任一查询失败（表缺失/列不存在）不拖垮接口，保证工作台必能加载
        try {
            // 本周/期内上新菜品数（created_at >= since 且 approved）
            vo.setNewDishCount(dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                    .ge(Dish::getCreatedAt, since)
                    .eq(Dish::getAuditStatus, DishConst.AUDIT_APPROVED)));
            // 期内新增评价数
            vo.setNewReviewCount(reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                    .ge(Review::getCreatedAt, since)));
            vo.setTotalDishCount(dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                    .eq(Dish::getAuditStatus, DishConst.AUDIT_APPROVED)));
            vo.setTotalReviewCount(reviewMapper.selectCount(new LambdaQueryWrapper<>()));

            // 最热门菜品（按浏览量降序 top5）
            vo.setHotDishes(dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                            .eq(Dish::getAuditStatus, DishConst.AUDIT_APPROVED)
                            .orderByDesc(Dish::getViewCount))
                    .stream().limit(5)
                    .map(d -> {
                        DashboardVO.RankItem item = new DashboardVO.RankItem();
                        item.setId(d.getId());
                        item.setName(d.getName());
                        item.setScore(d.getViewCount() == null ? 0L : d.getViewCount().longValue());
                        return item;
                    }).toList());

            // 最热门食堂（按下属菜品浏览量汇总 top5）
            vo.setHotCanteens(buildHotCanteens());

            // 趋势：每日新增菜品 + 每日新增评价
            vo.setViewTrend(buildTrend(since, range, true));
            vo.setReviewTrend(buildTrend(since, range, false));
        } catch (Exception ignored) {
            // 主体统计失败：保留空指标，工作台其余部分（待办/明细）仍正常
        }

        // ===== 规模指标 / 待办 / 明细 / 近期操作（逐项容错） =====
        fillExtended(vo);

        return Result.success(vo);
    }

    /** 扩展指标/待办/明细/近期操作：每一项独立 try-catch，失败给默认值，保证工作台始终可加载 */
    private void fillExtended(DashboardVO vo) {
        try { vo.setTotalCanteenCount(canteenMapper.selectCount(new LambdaQueryWrapper<>())); } catch (Exception ignored) { vo.setTotalCanteenCount(0L); }
        try { vo.setTotalStallCount(stallMapper.selectCount(new LambdaQueryWrapper<>())); } catch (Exception ignored) { vo.setTotalStallCount(0L); }
        try { vo.setTotalUserCount(userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "student"))); } catch (Exception ignored) { vo.setTotalUserCount(0L); }
        try { vo.setTotalMomentCount(momentMapper.selectCount(new LambdaQueryWrapper<>())); } catch (Exception ignored) { vo.setTotalMomentCount(0L); }
        try { vo.setTotalApplyCount(applyActionMapper.selectCount(new LambdaQueryWrapper<>())); } catch (Exception ignored) { vo.setTotalApplyCount(0L); }
        try { vo.setTotalFeedbackCount(feedbackMapper.selectCount(new LambdaQueryWrapper<>())); } catch (Exception ignored) { vo.setTotalFeedbackCount(0L); }

        try { vo.setPendingApplyCount(applyActionMapper.selectCount(new LambdaQueryWrapper<ApplyAction>().eq(ApplyAction::getStatus, "pending"))); } catch (Exception ignored) { vo.setPendingApplyCount(0L); }
        try { vo.setPendingMomentCount(momentMapper.selectCount(new LambdaQueryWrapper<Moment>().eq(Moment::getAuditStatus, "pending"))); } catch (Exception ignored) { vo.setPendingMomentCount(0L); }
        try { vo.setPendingFeedbackCount(feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>().eq(Feedback::getStatus, "pending"))); } catch (Exception ignored) { vo.setPendingFeedbackCount(0L); }

        try { vo.setPendingApplies(buildPendingApplies()); } catch (Exception ignored) { vo.setPendingApplies(List.of()); }
        try { vo.setPendingMoments(buildPendingMoments()); } catch (Exception ignored) { vo.setPendingMoments(List.of()); }
        try { vo.setPendingFeedbacks(buildPendingFeedbacks()); } catch (Exception ignored) { vo.setPendingFeedbacks(List.of()); }
        try { vo.setRecentLogs(buildRecentLogs()); } catch (Exception ignored) { vo.setRecentLogs(List.of()); }
    }

    // ===== 工作台待办明细 / 近期操作 =====

    private List<DashboardVO.TodoItem> buildPendingApplies() {
        return applyActionMapper.selectList(new LambdaQueryWrapper<ApplyAction>()
                        .eq(ApplyAction::getStatus, "pending")
                        .orderByDesc(ApplyAction::getCreatedAt)
                        .last("LIMIT 5"))
                .stream().map(a -> {
                    DashboardVO.TodoItem item = new DashboardVO.TodoItem();
                    item.setId(a.getId());
                    item.setType(a.getEntityType());
                    item.setTitle(entityLabel(a.getEntityType()) + "申请");
                    item.setTime(a.getCreatedAt() == null ? "" : a.getCreatedAt().format(DT_FMT));
                    return item;
                }).toList();
    }

    private List<DashboardVO.TodoItem> buildPendingMoments() {
        return momentMapper.selectList(new LambdaQueryWrapper<Moment>()
                        .eq(Moment::getAuditStatus, "pending")
                        .orderByDesc(Moment::getCreatedAt)
                        .last("LIMIT 5"))
                .stream().map(m -> {
                    DashboardVO.TodoItem item = new DashboardVO.TodoItem();
                    item.setId(m.getId());
                    item.setType("moment");
                    item.setTitle(abbrev(m.getContent(), 24));
                    item.setTime(m.getCreatedAt() == null ? "" : m.getCreatedAt().format(DT_FMT));
                    return item;
                }).toList();
    }

    private List<DashboardVO.TodoItem> buildPendingFeedbacks() {
        return feedbackMapper.selectList(new LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getStatus, "pending")
                        .orderByDesc(Feedback::getCreatedAt)
                        .last("LIMIT 5"))
                .stream().map(f -> {
                    DashboardVO.TodoItem item = new DashboardVO.TodoItem();
                    item.setId(f.getId());
                    item.setType(f.getType());
                    item.setTitle(abbrev(f.getContent(), 24));
                    item.setTime(f.getCreatedAt() == null ? "" : f.getCreatedAt().format(DT_FMT));
                    return item;
                }).toList();
    }

    private List<DashboardVO.RecentLogItem> buildRecentLogs() {
        List<OperationLog> logs = operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                .orderByDesc(OperationLog::getCreatedAt)
                .last("LIMIT 10"));
        if (logs.isEmpty()) return List.of();
        // 昵称可能为 null，toMap 的 value 不允许 null（否则 NPE），用 forEach 兜底空串
        Map<Long, String> adminNames = new HashMap<>();
        userMapper.selectBatchIds(logs.stream().map(OperationLog::getAdminId).distinct().toList())
                .forEach(u -> adminNames.put(u.getId(), u.getNickname() == null ? "" : u.getNickname()));
        return logs.stream().map(l -> {
            DashboardVO.RecentLogItem item = new DashboardVO.RecentLogItem();
            item.setId(l.getId());
            item.setOperator(adminNames.getOrDefault(l.getAdminId(), "管理员#" + l.getAdminId()));
            item.setAction(l.getAction());
            item.setTarget((l.getTargetType() == null ? "" : l.getTargetType())
                    + (l.getTargetId() == null ? "" : "#" + l.getTargetId()));
            item.setTime(l.getCreatedAt() == null ? "" : l.getCreatedAt().format(DT_FMT));
            return item;
        }).toList();
    }

    private String entityLabel(String entityType) {
        return switch (entityType == null ? "" : entityType) {
            case "dish" -> "菜品";
            case "stall" -> "档口";
            case "canteen" -> "食堂";
            default -> "内容";
        };
    }

    private String abbrev(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }

    private List<DashboardVO.RankItem> buildHotCanteens() {
        List<Canteen> canteens = canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                .eq(Canteen::getStatus, "open"));
        List<Stall> stalls = stallMapper.selectList(new LambdaQueryWrapper<Stall>());
        Map<Long, String> stallNameMap = stalls.stream()
                .collect(Collectors.toMap(Stall::getId, Stall::getName, (a, b) -> a));
        Map<Long, Long> canteenView = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getAuditStatus, DishConst.AUDIT_APPROVED))
                .stream()
                .collect(Collectors.groupingBy(Dish::getStallId,
                        Collectors.summingLong(d -> d.getViewCount() == null ? 0L : d.getViewCount().longValue())));
        Map<Long, Long> stallToCanteenView = new LinkedHashMap<>();
        stalls.forEach(s -> stallToCanteenView.merge(s.getCanteenId(),
                canteenView.getOrDefault(s.getId(), 0L), Long::sum));
        return stallToCanteenView.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(e -> {
                    DashboardVO.RankItem item = new DashboardVO.RankItem();
                    item.setId(e.getKey());
                    Canteen c = canteens.stream().filter(x -> x.getId().equals(e.getKey())).findFirst().orElse(null);
                    item.setName(c != null ? c.getName() : "未知食堂");
                    item.setScore(e.getValue());
                    return item;
                }).toList();
    }

    private DashboardVO.TrendData buildTrend(LocalDateTime since, int range, boolean isView) {
        DashboardVO.TrendData trend = new DashboardVO.TrendData();
        List<String> dates = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        for (int i = range - 1; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            dates.add(day.format(DATE_FMT));
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
            long count;
            if (isView) {
                // 浏览量趋势：每日新增菜品产生的初始浏览增量近似（无独立浏览事件表，用每日上新菜品数近似）
                count = dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                        .ge(Dish::getCreatedAt, dayStart)
                        .lt(Dish::getCreatedAt, dayEnd)
                        .eq(Dish::getAuditStatus, DishConst.AUDIT_APPROVED));
            } else {
                count = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                        .ge(Review::getCreatedAt, dayStart)
                        .lt(Review::getCreatedAt, dayEnd));
            }
            values.add(count);
        }
        trend.setDates(dates);
        trend.setValues(values);
        return trend;
    }
}
