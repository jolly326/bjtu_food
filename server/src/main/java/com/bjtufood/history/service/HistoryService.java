package com.bjtufood.history.service;

/**
 * 浏览足迹服务接口
 */
public interface HistoryService {

    /**
     * 记录一次菜品浏览足迹（去重 upsert 语义：同 userId+targetId 仅更新浏览时间，不重复插入）。
     * <p>
     * 在菜品浏览量自增（DishServiceImpl.addViewCount）前调用。
     * <p>
     * <b>2026-09-23 §7.41：本方法不再为任何去重提供判据</b> —— 浏览量已改 PV 口径（每次调用均 +1），
     * 足迹仅作为「谁看过这道菜」的行为记录保留（管理端行为查看用）。原 `existsTodayDishView`
     * （当日去重判据）已随之删除。
     *
     * @param userId 浏览者用户ID（可为 null / 游客，游客不记录足迹）
     * @param dishId 被浏览菜品ID
     */
    void recordDishView(Long userId, Long dishId);
}