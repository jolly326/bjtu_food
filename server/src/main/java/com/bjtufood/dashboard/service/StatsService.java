package com.bjtufood.dashboard.service;

import com.bjtufood.dashboard.dto.DashboardVO;

/**
 * 工作台统计服务（只读）
 * <p>
 * BE-03：统计逻辑原散落在 {@code dish.controller.admin.StatsController}（一个 @RestController 却
 * 注入 7 个 Mapper、无 @RequestMapping、无端点），并被 DashboardController 以 Controller→Controller
 * 方式调用，破坏四层分层。此处整体下沉为 Service，Controller 只保留参数与响应包装。
 * <p>
 * 全部为聚合查询，<b>不加事务</b>（只读、无需原子性）；各项独立容错，任一查询失败不影响其余指标。
 * <p>
 * 2026-09-15 用户拍板：工作台域自 {@code dish} 包迁出至独立 {@code dashboard} 包，接口语义与端点零变化。
 */
public interface StatsService {

    /**
     * 工作台总览：规模指标 + 待办明细 + 近期操作。
     *
     * @param range 统计窗口天数（7 / 30 / 90，其余值归一化为 7）
     * @return 工作台 VO
     */
    DashboardVO overview(int range);
}
