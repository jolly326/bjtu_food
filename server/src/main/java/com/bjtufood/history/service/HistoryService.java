package com.bjtufood.history.service;

/**
 * 浏览足迹服务接口
 */
public interface HistoryService {

    /**
     * 记录一次菜品浏览足迹（去重 upsert 语义：同 userId+targetId 仅更新浏览时间，不重复插入）。
     * <p>
     * 在菜品浏览量自增（DishServiceImpl.addViewCount）前调用，为「当日去重」提供判据。
     *
     * @param userId 浏览者用户ID（可能为 null/游客，游客不记录足迹）
     * @param dishId 被浏览菜品ID
     */
    void recordDishView(Long userId, Long dishId);

    /**
     * 判断当前用户「当天」（自然日，业务时区 Asia/Shanghai）是否已浏览过该菜品。
     * <p>
     * 浏览量去重真源（2026-09-14 §7.14 A / BE-02 修正）：同一天内同一用户对同一菜品只计 1 次浏览量。
     * <b>判据字段为 updated_at 而非 created_at</b>——upsert 只刷新 updated_at，
     * 若按 created_at（首次浏览时刻）判重，则今日重复浏览永远查不到当日记录、判据恒 false。
     * 跨天场景：昨日的足迹行 updated_at 落在昨天，今日重新命中 → 正常计 1 次。
     *
     * @param userId 浏览者用户ID（null/游客直接返回 false，游客不做当日去重）
     * @param dishId 被浏览菜品ID
     * @return true=当天已有浏览记录（应幂等跳过计数）
     */
    boolean existsTodayDishView(Long userId, Long dishId);
}