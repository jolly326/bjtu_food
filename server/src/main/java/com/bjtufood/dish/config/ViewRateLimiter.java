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
 * <p>
 * ⚠️ 职责边界（限流器统一评审结论，<b>刻意不与 IpRateLimiter 合并</b>）：
 * 本类语义为<b>幂等 / 去重</b>——命中窗口时仅让调用方<b>跳过计数</b>
 * （见 DishServiceImpl#addViewCount 直接 return，接口仍返回成功），<b>不拒绝请求</b>；
 * key 空间为 userId:dishId，输出为「窗口内是否首次」布尔值。
 * 而 {@link com.bjtufood.common.config.IpRateLimiter} 语义为<b>请求节流</b>——
 * 超限时由调用方抛 400 阻断请求；key 空间为 scope:IP，支持多条规则并返回等待秒数。
 * 二者 key 空间、返回语义、调用方处置方式均不同（去重 vs 阻断），
 * 强行合并为一个类会引入「布尔/秒数」双语义分支，故保持独立实现。
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
