package com.bjtufood.history.service;

/**
 * 浏览足迹服务接口
 */
public interface HistoryService {

    /**
     * 取当前用户最近 N 条浏览过的菜品ID（个性化「猜你喜欢」使用）
     */
    java.util.List<Long> recentViewedDishIds(Long userId, int limit);

    /**
     * 记录一次菜品浏览足迹（去重 upsert 语义：同 userId+targetId 仅更新浏览时间，不重复插入）。
     * <p>
     * 修复：原 view_log 只有读取无写入，导致「猜你喜欢」个性化数据缺失。
     * 应在菜品浏览量自增（DishServiceImpl.addViewCount）时同步记录。
     *
     * @param userId 浏览者用户ID（可能为 null/游客，游客不记录足迹）
     * @param dishId 被浏览菜品ID
     */
    void recordDishView(Long userId, Long dishId);

    /**
     * 判断当前用户「当天」（自然日，服务器时区 Asia/Shanghai）是否已浏览过该菜品。
     * <p>
     * 浏览量去重真源（2026-09-14 §7.14 A）：同一天内同一用户对同一菜品只计 1 次浏览量，
     * 依据 view_log 中 user_id + target_type='dish' + target_id 的 created_at 是否落在今日。
     * 存在旧足迹行（updated_at 被 upsert 刷新但 created_at 仍是首次浏览时刻）时，
     * 今日重复浏览不会命中本判定，从而正确计为「新的一天」。
     *
     * @param userId 浏览者用户ID（null/游客直接返回 false，游客不做当日去重）
     * @param dishId 被浏览菜品ID
     * @return true=当天已有浏览记录（应幂等跳过计数与插记录）
     */
    boolean existsTodayDishView(Long userId, Long dishId);
}
