package com.bjtufood.common.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP 维度滑动窗口限频（进程内内存实现：ConcurrentHashMap + 容量上限 + @Scheduled 定时清理）。
 * <p>
 * 本类为项目内**唯一的进程内限频实现**。
 * <p>
 * 适用 permitAll 公开写入口的滥用防护（如 POST /feedback 灌库、POST /auth/email-code
 * 换邮箱刷码、POST /dishes/{id}/correction 刷纠错）：同一客户端 IP 在窗口内的请求次数受多条规则共同约束（如
 * 每分钟 ≤2 条 + 每小时 ≤10 条）。状态仅存 JVM 内存、重启清零（可接受：
 * 限频为攻防止损，不要求跨实例强一致）。
 * <p>
 * 实现说明：
 * <ul>
 *   <li>每个 key（scope:ip）维护窗口内请求时间戳升序队列，检查与记录在
 *       synchronized 内原子完成，避免并发请求同时通过；check 全部通过才记录，
 *       被拒绝的请求不消耗额度。</li>
 *   <li>限频发生在低频写操作路径（提交反馈/发验证码），方法级锁开销可忽略。</li>
 *   <li>规则窗口不得超过 {@link #MAX_WINDOW_MS}（当前业务规则最大 1 小时），
 *       清理线程据此判定过期条目。</li>
 * </ul>
 */
@Component
public class IpRateLimiter {

    /** 单条限频规则：windowMs 毫秒窗口内最多 limit 次 */
    public record Rule(int limit, long windowMs) {
    }

    /** 业务规则窗口上限（当前所有规则 ≤ 1 小时），清理与滑动判定据此对齐 */
    private static final long MAX_WINDOW_MS = 60L * 60 * 1000;

    /** 内存兜底容量上限，超过时先做一次清理，避免异常流量下 key 无限增长 */
    private static final int MAX_ENTRIES = 10_000;

    /** key = scope:ip，value = 该键在窗口内的请求时间戳队列（升序） */
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * 尝试获取一次配额。
     * <p>
     * 全部规则通过则记录本次时间戳并返回 0（放行）；任一规则已达上限则返回
     * 需等待的秒数（向上取整），本次不记录、不消耗额度。
     *
     * @param scope 业务隔离域（如 "feedback" / "email-code"），不同接口互不干扰
     * @param ip    客户端IP（ClientIpUtil 解析；为空时放行——fail-open，
     *              不因解析失败误伤正常请求，且公开写入口仍有业务校验兜底）
     * @param rules 限频规则（可多条，如「每分钟 ≤2 条 + 每小时 ≤10 条」）
     * @return 0=放行；&gt;0=需等待的秒数
     */
    public long tryAcquire(String scope, String ip, Rule... rules) {
        if (scope == null || scope.isBlank() || ip == null || ip.isBlank() || rules.length == 0) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        if (hits.size() > MAX_ENTRIES) {
            cleanup();
        }
        String key = scope + ":" + ip;
        long waitSeconds = 0L;
        synchronized (this) {
            Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
            // 先滑出最老时间戳：队列内只保留最大规则窗口内的记录
            while (!window.isEmpty() && now - window.peekFirst() > MAX_WINDOW_MS) {
                window.pollFirst();
            }
            for (Rule rule : rules) {
                if (rule == null || rule.limit() <= 0) {
                    continue;
                }
                long count = 0;
                long oldest = Long.MAX_VALUE;
                for (Long t : window) {
                    if (now - t <= rule.windowMs()) {
                        count++;
                        oldest = Math.min(oldest, t);
                    }
                }
                if (count >= rule.limit()) {
                    // 等待窗口内最早一次命中滑出窗口
                    long waitMs = rule.windowMs() - (now - oldest);
                    waitSeconds = Math.max(waitSeconds, (waitMs + 999) / 1000);
                }
            }
            if (waitSeconds > 0) {
                return waitSeconds;
            }
            window.addLast(now);
        }
        return 0L;
    }

    /**
     * 每分钟清理滑出最大窗口的队列并移除空 key（与 TokenBlacklist 同节奏），
     * 防内存缓慢增长。synchronized 与 tryAcquire 互斥，避免遍历期间并发修改。
     */
    @Scheduled(fixedDelay = 60_000)
    public synchronized void cleanup() {
        long now = System.currentTimeMillis();
        hits.entrySet().removeIf(e -> {
            Deque<Long> q = e.getValue();
            q.removeIf(t -> now - t > MAX_WINDOW_MS);
            return q.isEmpty();
        });
    }
}
