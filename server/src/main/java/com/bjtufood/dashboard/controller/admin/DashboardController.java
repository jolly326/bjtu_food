package com.bjtufood.dashboard.controller.admin;

import com.bjtufood.common.result.Result;
import com.bjtufood.dashboard.dto.DashboardVO;
import com.bjtufood.dashboard.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 数据工作台（权威路径 /admin/dashboard）
 * <p>
 * BE-03：统计逻辑已下沉 {@link StatsService}，本 Controller 只做「参数归一化 + 响应包装」，
 * 不再出现 Controller→Controller 与 Controller 直调 Mapper。
 * 2026-09-14 用户拍板（Q-106）：工作台不含图表看板，仅返回待办 + 规模指标 + 近期操作。
 * 2026-09-15 用户拍板：工作台域自 {@code dish} 包迁出至独立 {@code dashboard} 包，端点路径与响应零变化。
 */
@Tag(name = "数据看板", description = "运营工作台：规模指标、待办明细、近期操作。需要管理员口令。")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final StatsService statsService;

    @Operation(summary = "工作台总览", description = "用途：运营工作台。支持 range=week/month/all，默认 week。返回规模指标、待办明细（最近 5 条）与近期操作（最近 10 条）。")
    @GetMapping
    public Result<DashboardVO> dashboard(@RequestParam(defaultValue = "week") String range) {
        // Web 后台以字符串枚举（week/month/all）传参，后端映射为天数后交给 StatsService。
        // all=90 天：覆盖学期内主要运营周期，避免「全部」与 month 语义混同
        int days = switch (range) {
            case "month" -> 30;
            case "all" -> 90;
            default -> 7;
        };
        return Result.success(statsService.overview(days));
    }
}
