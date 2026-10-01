package com.bjtufood.common.ratelimit;

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
 *       <b>该 key 自己的队列监视器</b>内原子完成，避免并发请求同时通过；check 全部通过才记录，
 *       被拒绝的请求不消耗额度。</li>
 *   <li><b>锁粒度 = 单个客户端</b>（P1）：原实现是 {@code synchronized(this)} 全局锁，
 *       8 线程压测下拒绝分支比放行分支慢约 40%（基线 §3）——所有 IP 的判定串行在一把锁上。
 *       改为按 key 加锁后，不同 IP 互不阻塞，同一 IP 仍严格串行（限流语义不变）。</li>
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

    /** 撞上清理竞态时的最大重试轮数 */
    private static final int ACQUIRE_RETRIES = 3;

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
        // 至多重试 ACQUIRE_RETRIES 轮：清理线程可能在本线程取到队列之后把它摘出 map
        // （键已整窗过期）。不校验归属就写队列，那次命中会写进一个已脱离 map 的队列里
        // ——表现为「静默少计一次配额」，方向偏宽松，正是限流最不该有的偏差。
        for (int attempt = 0; attempt < ACQUIRE_RETRIES; attempt++) {
            Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
            // 锁加在队列对象上：同一客户端严格串行，不同客户端互不阻塞
            synchronized (window) {
                if (hits.get(key) != window) {
                    continue;
                }
                // 先滑出最老时间戳：队列内只保留最大规则窗口内的记录
                while (!window.isEmpty() && now - window.peekFirst() > MAX_WINDOW_MS) {
                    window.pollFirst();
                }
                long waitSeconds = 0L;
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
                return 0L;
            }
        }
        // 连续 ACQUIRE_RETRIES 轮都撞上清理竞态（实际概率极低）：放行，与「IP 解析失败即放行」同口径
        return 0L;
    }

    /**
     * 每分钟清理滑出最大窗口的队列并移除空 key（与 TokenBlacklist 同节奏），防内存缓慢增长。
     * <p>
     * 逐 key 持其队列监视器后判定，因此<b>不再需要全局锁</b>；摘除 key 与 {@code tryAcquire}
     * 的竞态由后者的归属复检（{@code hits.get(key) == window}）兜住。
     */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        hits.entrySet().removeIf(e -> {
            Deque<Long> q = e.getValue();
            synchronized (q) {
                q.removeIf(t -> now - t > MAX_WINDOW_MS);
                return q.isEmpty();
            }
        });
    }
}
