package com.bjtufood.dish.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
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
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.mapper.ReviewMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作台统计控制器（Web 后台 dashboard）
 * <p>
 * 2026-09-14 用户拍板（Q-106）：工作台不含图表看板，前端不消费热度排行 / 趋势字段，
 * 故不再执行全表菜品聚合与逐日趋势查询，仅保留「待办 + 规模指标 + 近期操作」。
 * <p>
 * 本类不暴露任何 HTTP 端点（无 @RequestMapping / @GetMapping），仅作为 DashboardController 的
 * 逻辑复用载体；原 api-design.md 记载的 GET /admin/stats/** 为幽灵端点（全仓零实现），不新建。
 */
@Tag(name = "数据统计（工作台）", description = "工作台总览：规模指标、待办明细、近期操作。逻辑由 DashboardController 复用。")
@RestController
@RequiredArgsConstructor
public class StatsController {

    private final DishMapper dishMapper;
    private final ReviewMapper reviewMapper;
    private final CanteenMapper canteenMapper;
    private final StallMapper stallMapper;
    private final UserMapper userMapper;
    private final FeedbackMapper feedbackMapper;
    private final OperationLogMapper operationLogMapper;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 工作台总览：由 DashboardController 直接调用复用，不再暴露独立 HTTP 端点 */
    public Result<DashboardVO> overview(int range) {
        // 支持 week(7)/month(30)/all(90)；修复此前 range=all 被吞回 7 天的问题
        if (range != 7 && range != 30 && range != 90) range = 7;
        LocalDateTime since = LocalDate.now().minusDays(range).atStartOfDay();
        DashboardVO vo = new DashboardVO();
        vo.setRange("近" + range + "天");

        // 规模指标（逐项容错，任一查询失败不拖垮接口，保证工作台必能加载）
        try {
            // 期内上新菜品数（created_at >= since 且 approved）
            vo.setNewDishCount(dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                    .ge(Dish::getCreatedAt, since)
                    .eq(Dish::getAuditStatus, DishConst.AUDIT_APPROVED)));
            // 期内新增评价数
            vo.setNewReviewCount(reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                    .ge(Review::getCreatedAt, since)));
            vo.setTotalDishCount(dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                    .eq(Dish::getAuditStatus, DishConst.AUDIT_APPROVED)));
            vo.setTotalReviewCount(reviewMapper.selectCount(new LambdaQueryWrapper<>()));
        } catch (Exception ignored) {
            // 规模统计失败：保留空指标，工作台其余部分（待办/明细/近期操作）仍正常
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
        try { vo.setTotalFeedbackCount(feedbackMapper.selectCount(new LambdaQueryWrapper<>())); } catch (Exception ignored) { vo.setTotalFeedbackCount(0L); }

        try { vo.setPendingFeedbackCount(feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>().eq(Feedback::getStatus, "pending"))); } catch (Exception ignored) { vo.setPendingFeedbackCount(0L); }

        try { vo.setPendingFeedbacks(buildPendingFeedbacks()); } catch (Exception ignored) { vo.setPendingFeedbacks(List.of()); }
        try { vo.setRecentLogs(buildRecentLogs()); } catch (Exception ignored) { vo.setRecentLogs(List.of()); }
    }

    // ===== 工作台待办明细 / 近期操作 =====

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

    private String abbrev(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }
}
