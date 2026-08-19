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
}
