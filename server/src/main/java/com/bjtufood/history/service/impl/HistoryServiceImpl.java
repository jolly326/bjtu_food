package com.bjtufood.history.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bjtufood.history.entity.ViewLog;
import com.bjtufood.history.mapper.ViewLogMapper;
import com.bjtufood.history.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 浏览足迹服务实现
 */
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final ViewLogMapper viewLogMapper;

    @Override
    public List<Long> recentViewedDishIds(Long userId, int limit) {
        if (limit < 1) limit = 20;
        return viewLogMapper.selectList(new LambdaQueryWrapper<ViewLog>()
                        .eq(ViewLog::getUserId, userId)
                        .eq(ViewLog::getTargetType, "dish")
                        .orderByDesc(ViewLog::getCreatedAt)
                        .last("LIMIT " + limit))
                .stream()
                .map(ViewLog::getTargetId)
                .distinct()
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordDishView(Long userId, Long dishId) {
        if (userId == null || dishId == null) {
            // 游客（未登录）或参数缺失不记录足迹，仅统计浏览量由调用方负责
            return;
        }
        // 去重 upsert：同 userId+targetType=dish+targetId 已存在则仅更新浏览时间，
        // 不存在则插入一条；避免重复浏览产生重复足迹行，保持「猜你喜欢」去重读取有意义。
        int updated = viewLogMapper.update(new LambdaUpdateWrapper<ViewLog>()
                .eq(ViewLog::getUserId, userId)
                .eq(ViewLog::getTargetType, "dish")
                .eq(ViewLog::getTargetId, dishId)
                .setSql("updated_at = NOW()"));
        if (updated == 0) {
            ViewLog log = new ViewLog();
            log.setUserId(userId);
            log.setTargetType("dish");
            log.setTargetId(dishId);
            viewLogMapper.insert(log);
        }
    }
}
