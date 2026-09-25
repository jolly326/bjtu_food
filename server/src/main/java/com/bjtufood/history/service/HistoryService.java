package com.bjtufood.history.service;

/**
 * 访问日志服务。
 *
 * <p>语义（append-only 访问日志）：**每次浏览 INSERT 一行**，不做更新与合并 ——
 * 作为时间窗口聚合（近一个月 / 任意窗口最热菜品）的数据基础；
 * {@code dish.view_count}（全历史累计，供热度排序）与日志**并存不混用**。
 * 游客亦写入（{@code user_id=0}），保证窗口统计反映真实访问量。
 *
 * @param userId 浏览者用户ID（null = 游客，日志记 {@code user_id=0}）
 * @param dishId 被浏览菜品ID
 */
public interface HistoryService {

    /**
     * 记录一次菜品访问（INSERT 一行日志）。
     *
     * @param userId 浏览者用户ID（可为 null = 游客）
     * @param dishId 被浏览菜品ID
     */
    void recordDishView(Long userId, Long dishId);
}
