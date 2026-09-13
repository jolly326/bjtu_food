package com.bjtufood.dish.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 浏览量防刷去重窗口（进程内内存实现，组件风格对齐 auth 包的 TokenBlacklist）。
 * <p>
 * 同一用户对同一菜品在 5 分钟窗口内只计 1 次有效浏览，抑制脚本高频刷
 * 「浏览量」导致热门榜 / 推荐位失真。窗口状态仅存 JVM 内存、重启清零
 * （可接受：防刷属统计降噪，不要求跨实例强一致）；设容量上限 + 定时清理，
 * 防 key 无限增长造成内存泄漏。
 */
@Component
public class ViewRateLimiter {

    /** 去重窗口：5 分钟 */
    private static final long WINDOW_MS = 5L * 60 * 1000;

    /** 内存兜底容量上限，超过时先做一次清理，避免异常流量下无限增长 */
    private static final int MAX_ENTRIES = 100_000;

    /** key = userId:dishId，value = 窗口过期时间戳 */
    private final Map<String, Long> viewed = new ConcurrentHashMap<>();

    /**
     * 记录一次浏览：窗口内首次返回 true（应计数），窗口内重复返回 false（忽略）。
     * 通过 {@code compute} 原子执行判定与写入，避免并发请求同时通过。
     *
     * @param userId 浏览者用户ID（游客为 null，不设限直接放行）
     * @param dishId 被浏览菜品ID
     */
    public boolean tryAcquire(Long userId, Long dishId) {
        if (userId == null || dishId == null) {
            return true;
        }
        String key = userId + ":" + dishId;
        long now = System.currentTimeMillis();
        if (viewed.size() > MAX_ENTRIES) {
            cleanup();
        }
        boolean[] allowed = {true};
        viewed.compute(key, (k, expireAt) -> {
            if (expireAt != null && expireAt > now) {
                allowed[0] = false;
                return expireAt;
            }
            return now + WINDOW_MS;
        });
        return allowed[0];
    }

    /** 每分钟清理已滑出窗口的条目（与 TokenBlacklist 同节奏），防内存缓慢增长 */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        viewed.entrySet().removeIf(e -> e.getValue() <= now);
    }
}
