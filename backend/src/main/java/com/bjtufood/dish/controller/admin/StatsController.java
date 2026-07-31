package com.bjtufood.dish.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.common.result.Result;
import com.bjtufood.dish.dto.DashboardVO;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
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

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");

    @Operation(summary = "数据看板总览", description = "用途：运营数据一览。支持 range=7/30（天），默认 7 天。返回本周上新、本周评价、热门排行与趋势。")
    @GetMapping("/overview")
    public Result<DashboardVO> overview(@RequestParam(defaultValue = "7") int range) {
        if (range != 7 && range != 30) range = 7;
        LocalDateTime since = LocalDate.now().minusDays(range).atStartOfDay();
        DashboardVO vo = new DashboardVO();
        vo.setRange("近" + range + "天");

        // 本周/期内上新菜品数（created_at >= since 且 approved）
        Long newDishCount = dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .ge(Dish::getCreatedAt, since)
                .eq(Dish::getAuditStatus, "approved"));
        vo.setNewDishCount(newDishCount);

        // 期内新增评价数
        Long newReviewCount = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .ge(Review::getCreatedAt, since));
        vo.setNewReviewCount(newReviewCount);

        vo.setTotalDishCount(dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getAuditStatus, "approved")));
        vo.setTotalReviewCount(reviewMapper.selectCount(new LambdaQueryWrapper<>()));

        // 最热门菜品（按浏览量降序 top5）
        List<DashboardVO.RankItem> hotDishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getAuditStatus, "approved")
                        .orderByDesc(Dish::getViewCount))
                .stream().limit(5)
                .map(d -> {
                    DashboardVO.RankItem item = new DashboardVO.RankItem();
                    item.setId(d.getId());
                    item.setName(d.getName());
                    item.setScore(d.getViewCount() == null ? 0L : d.getViewCount().longValue());
                    return item;
                }).toList();
        vo.setHotDishes(hotDishes);

        // 最热门食堂（按下属菜品浏览量汇总 top5）
        vo.setHotCanteens(buildHotCanteens());

        // 趋势：每日新增菜品 + 每日新增评价
        vo.setViewTrend(buildTrend(since, range, true));
        vo.setReviewTrend(buildTrend(since, range, false));

        return Result.success(vo);
    }

    private List<DashboardVO.RankItem> buildHotCanteens() {
        List<Canteen> canteens = canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                .eq(Canteen::getStatus, "open"));
        List<Stall> stalls = stallMapper.selectList(new LambdaQueryWrapper<Stall>());
        Map<Long, String> stallNameMap = stalls.stream()
                .collect(Collectors.toMap(Stall::getId, Stall::getName, (a, b) -> a));
        Map<Long, Long> canteenView = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getAuditStatus, "approved"))
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
                        .eq(Dish::getAuditStatus, "approved"));
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
