package com.bjtufood.dish.controller.admin;

import com.bjtufood.common.result.Result;
import com.bjtufood.dish.dto.DashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 数据看板（权威路径 /admin/dashboard）
 * <p>
 * 内部复用 StatsController 的统计逻辑，仅为 Web 后台提供契约约定的对外路径。
 */
@Tag(name = "数据看板", description = "运营数据一览：上新/评价指标、热门排行、趋势图。需要管理员 token。")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final StatsController statsController;

    @Operation(summary = "数据看板总览", description = "用途：运营数据一览。支持 range=week/month/all，默认 week。返回本周上新、本周评价、热门排行与趋势。")
    @GetMapping
    public Result<DashboardVO> dashboard(@RequestParam(defaultValue = "week") String range) {
        // Web 后台以字符串枚举（week/month/all）传参，后端映射为天数后复用 StatsController。
        int days = switch (range) {
            case "month", "all" -> 30;
            default -> 7;
        };
        return statsController.overview(days);
    }
}
