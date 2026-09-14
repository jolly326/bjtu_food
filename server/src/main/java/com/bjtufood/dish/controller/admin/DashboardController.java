package com.bjtufood.dish.controller.admin;

import com.bjtufood.common.result.Result;
import com.bjtufood.dish.dto.DashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 数据工作台（权威路径 /admin/dashboard）
 * <p>
 * 内部复用 StatsController 的统计逻辑，仅为 Web 后台提供契约约定的对外路径。
 * 2026-09-14 用户拍板（Q-106）：工作台不含图表看板，仅返回待办 + 规模指标 + 近期操作。
 */
@Tag(name = "数据看板", description = "运营工作台：规模指标、待办明细、近期操作。需要管理员口令。")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final StatsController statsController;

    @Operation(summary = "工作台总览", description = "用途：运营工作台。支持 range=week/month/all，默认 week。返回规模指标、待办明细（最近 5 条）与近期操作（最近 10 条）。")
    @GetMapping
    public Result<DashboardVO> dashboard(@RequestParam(defaultValue = "week") String range) {
        // Web 后台以字符串枚举（week/month/all）传参，后端映射为天数后复用 StatsController。
        // all=90 天：覆盖学期内主要运营周期，避免「全部」与 month 语义混同
        int days = switch (range) {
            case "month" -> 30;
            case "all" -> 90;
            default -> 7;
        };
        return statsController.overview(days);
    }
}
