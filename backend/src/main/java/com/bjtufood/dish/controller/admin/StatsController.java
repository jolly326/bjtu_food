package com.bjtufood.dish.controller.admin;

import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 数据统计控制器（食堂管理员专用）
 * <p>
 * 食堂管理员查看自己档口的数据统计看板。
 * 包括总览数据、趋势图、热门菜品排行。
 */
@Tag(name = "数据统计（食堂管理员）", description = "营业数据看板：总览、趋势、排行")
@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
public class StatsController {

    @Operation(summary = "数据总览", description = "浏览量、收藏量、平均评分、菜品总数等汇总数据")
    @GetMapping("/overview")
    public Result<?> overview() {
        // TODO: 获取当前用户的 stallId，查询统计汇总
        // 返回：{ totalViews, totalCollects, avgRating, totalDishes }
        return Result.success("统计总览");
    }

    @Operation(summary = "趋势数据", description = "近N天的浏览量/评价量趋势（默认7天）")
    @GetMapping("/trend")
    public Result<?> trend(@RequestParam(defaultValue = "7") int days) {
        // TODO: 按天聚合 view_count 和 review_count
        // 返回：{ dates: ["01-01",...], views: [...], ratings: [...] }
        return Result.success("趋势数据");
    }

    @Operation(summary = "热门菜品排行", description = "菜品按收藏量/评价数降序排列")
    @GetMapping("/rank")
    public Result<?> rank() {
        // TODO: 查询当前档口的热门菜品排行
        return Result.success("菜品排行");
    }
}
