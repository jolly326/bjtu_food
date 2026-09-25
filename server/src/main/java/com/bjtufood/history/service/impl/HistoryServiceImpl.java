package com.bjtufood.history.service.impl;

import com.bjtufood.history.entity.ViewLog;
import com.bjtufood.history.mapper.ViewLogMapper;
import com.bjtufood.history.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 访问日志服务实现（view_log = **append-only 访问日志**）。
 *
 * <p>每次浏览 INSERT 一行：不按用户 / 目标合并、不刷新任何已有行 ——
 * 每一行即一次真实访问（含游客，{@code user_id=0}），据此可按
 * {@code target_type + target_id + created_at} 聚合任意时间窗口的最热菜品。
 * {@code dish.view_count}（全历史累计）由调用方（详情成功路径）原子自增维护，
 * 与本日志**并存不混用**。
 */
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    /** 游客的 user_id 占位值（view_log.user_id 为 NOT NULL，0 = 未登录访问） */
    private static final long GUEST_USER_ID = 0L;

    private final ViewLogMapper viewLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordDishView(Long userId, Long dishId) {
        // append-only：每次访问恒 INSERT 一行；游客记 user_id=0，保证窗口统计完整
        ViewLog log = new ViewLog();
        log.setUserId(userId == null ? GUEST_USER_ID : userId);
        log.setTargetType("dish");
        log.setTargetId(dishId);
        // created_at 由 MybatisMetaObjectHandler 以 JVM 时钟填充（与 DB 时区解耦）
        viewLogMapper.insert(log);
    }
}
