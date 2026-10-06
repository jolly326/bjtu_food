package com.bjtufood.dish.task;

import com.bjtufood.dish.mapper.DishViewLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 菜品浏览日志清理定时任务（30 天滚动窗口）。
 *
 * <p><b>为什么必须清理</b>：{@code dish_view_log} 一行 = 一次浏览，不清理则表无限增长，
 * 且 {@code idx_viewed_at} 的范围扫描代价随行数线性上升。当前量级（25 菜 × 日均 50 次
 * ≈ 3.75 万行 / 30 天）远未到瓶颈，**每窗口清理一次即可**，无需分表或分区。
 *
 * <p><b>窗口口径</b>：严格 {@code NOW() - 30 天} 的<b>滑动窗口</b>（不是「本月 1 号起」——
 * 后者会在每月 1 日突然只剩几天数据，导致「近 30 天浏览」大幅跳变）。
 *
 * <p><b>定时时刻</b>：04:40 —— 与评分对账（04:10）、验证码清理（03:00）错开，
 * 避免三个全表任务挤在同一分钟争抢数据库负载。
 *
 * @see com.bjtufood.dish.entity.DishViewLog
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DishViewLogCleanupTask {

    /** 滚动窗口保留天数（与 {@code docs/schema/dish_view_log.md} §2.3 一致） */
    private static final int RETENTION_DAYS = 30;

    private final DishViewLogMapper dishViewLogMapper;

    /**
     * 每天 04:40 执行一次，删除早于 30 天的浏览日志。
     */
    @Scheduled(cron = "0 40 4 * * ?")
    public void cleanupExpiredViewLogs() {
        // 异常必须内部消化：调度线程抛出异常会导致该任务后续不再被触发
        try {
            int deleted = dishViewLogMapper.deleteOlderThan(RETENTION_DAYS);
            if (deleted > 0) {
                log.info("浏览日志清理完成：已删除 {} 条 {} 天前的记录", deleted, RETENTION_DAYS);
            }
        } catch (Exception e) {
            log.error("清理过期浏览日志失败，本次跳过（不影响下次执行）", e);
        }
    }
}