package com.bjtufood.history.service;

/**
 * 浏览足迹服务接口
 */
public interface HistoryService {

    /**
     * 取当前用户最近 N 条浏览过的菜品ID（个性化「猜你喜欢」使用）
     */
    java.util.List<Long> recentViewedDishIds(Long userId, int limit);
}
