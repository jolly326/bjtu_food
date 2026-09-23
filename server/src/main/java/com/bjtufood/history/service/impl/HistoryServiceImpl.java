package com.bjtufood.history.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bjtufood.history.entity.ViewLog;
import com.bjtufood.history.mapper.ViewLogMapper;
import com.bjtufood.history.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 浏览足迹服务实现
 * <p>
 * <b>2026-09-23 §7.41</b>：浏览量去重取消（改 PV 口径）后，足迹**不再参与计数判定**，
 * 仅作为「谁看过这道菜」的行为记录保留。原 `existsTodayDishView`（当日去重判据，含
 * Asia/Shanghai 自然日切分与 updated_at 判据说明）随之删除，其专属的
 * `Date` / `LocalDate` / `ZoneId` / `LambdaQueryWrapper` import 与 `VIEW_DEDUP_ZONE` 常量一并清理。
 */
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final ViewLogMapper viewLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordDishView(Long userId, Long dishId) {
        if (userId == null || dishId == null) {
            // 游客（未登录）或参数缺失不记录足迹，仅统计浏览量由调用方负责
            return;
        }
        // 去重 upsert：同 userId+targetType=dish+targetId 已存在则仅刷新 updated_at（不新增行），
        // 不存在则插入一条（本表现仅作浏览足迹，**不再作计数判据**）。
        // 时钟统一（2026-09-15）：update 分支显式传 JVM 时钟值（LocalDateTime.now()）作为参数，
        // 不再用 SQL NOW()（DB 时区）——DB 与 JVM 时区不一致时凌晨存在切天偏差；
        // insert 分支由 MybatisMetaObjectHandler 以同一 JVM 时钟填充 created_at/updated_at，二者同源。
        LocalDateTime now = LocalDateTime.now();
        int updated = viewLogMapper.update(new LambdaUpdateWrapper<ViewLog>()
                .eq(ViewLog::getUserId, userId)
                .eq(ViewLog::getTargetType, "dish")
                .eq(ViewLog::getTargetId, dishId)
                .set(ViewLog::getUpdatedAt, now));
        if (updated == 0) {
            ViewLog log = new ViewLog();
            log.setUserId(userId);
            log.setTargetType("dish");
            log.setTargetId(dishId);
            viewLogMapper.insert(log);
        }
    }
}
