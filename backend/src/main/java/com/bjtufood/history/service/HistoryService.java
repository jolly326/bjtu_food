package com.bjtufood.history.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.history.dto.ViewLogVO;

/**
 * 浏览足迹服务接口
 */
public interface HistoryService {

    /**
     * 记录一次浏览（学生态；游客不写）
     */
    void record(Long userId, String targetType, Long targetId);

    /**
     * 我的足迹列表（倒序），支持 targetType 过滤
     */
    IPage<ViewLogVO> listMyHistory(Long userId, String targetType, int page, int pageSize);

    /**
     * 删除单条足迹（归属校验）
     */
    void deleteOne(Long userId, Long id);

    /**
     * 清空本人全部足迹
     */
    void clearAll(Long userId);

    /**
     * 取当前用户最近 N 条浏览过的菜品ID（个性化「猜你喜欢」使用）
     */
    java.util.List<Long> recentViewedDishIds(Long userId, int limit);
}
