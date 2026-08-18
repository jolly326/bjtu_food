package com.bjtufood.history.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.history.entity.ViewLog;
import com.bjtufood.history.mapper.ViewLogMapper;
import com.bjtufood.history.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
