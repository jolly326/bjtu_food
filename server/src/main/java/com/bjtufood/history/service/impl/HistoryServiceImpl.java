package com.bjtufood.history.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bjtufood.history.entity.ViewLog;
import com.bjtufood.history.mapper.ViewLogMapper;
import com.bjtufood.history.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 浏览足迹服务实现
 */
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    /**
     * 自然日判定时区（浏览量去重口径）。
     * <p>
     * 与 JVM 默认时区解耦：服务器可能部署在 UTC 容器中，直接用 {@code CURDATE()} 会按 UTC 切天，
     * 导致北京时间 08:00 前的浏览被算作「前一天」而重复计数。此处以「今日零点（Asia/Shanghai）」
     * 作为时间下界显式下推给 SQL，保证口径与业务（校园本地时区）一致。
     */
    private static final ZoneId VIEW_DEDUP_ZONE = ZoneId.of("Asia/Shanghai");

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

    @Override
    public boolean existsTodayDishView(Long userId, Long dishId) {
        if (userId == null || dishId == null) {
            // 游客不做当日去重（浏览量上报接口本身要求登录，此处仅为防御性兜底）
            return false;
        }
        // 当天零点（Asia/Shanghai）→ java.sql.Date（时区无关的字面日期），由数据库按 DATETIME 比较
        Date todayStart = Date.valueOf(LocalDate.now(VIEW_DEDUP_ZONE));
        return viewLogMapper.selectCount(new LambdaQueryWrapper<ViewLog>()
                .eq(ViewLog::getUserId, userId)
                .eq(ViewLog::getTargetType, "dish")
                .eq(ViewLog::getTargetId, dishId)
                .ge(ViewLog::getCreatedAt, todayStart)) > 0;
    }
}
