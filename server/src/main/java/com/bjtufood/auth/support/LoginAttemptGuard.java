package com.bjtufood.auth.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理端登录的**账号维度**防护：失败计数 + 渐进锁定 + 指数退避（进程内内存实现）。
 *
 * <p><b>为什么必须有账号维度</b>：IP 维度限频防的是「同一来源狂打」，攻击者换 IP
 * （校园网多出口 / 代理池）即绕过；而管理员用户名固定且可猜，密码一旦被字典命中，
 * 管理端的破坏力（物理删菜品 + 级联删评价）<b>不可逆</b>。账号维度的计数按用户名累加，
 * <b>跨 IP 仍然生效</b>。
 *
 * <p><b>渐进锁定梯度</b>：5 次失败锁 5 分钟 → 10 次锁 1 小时 → 20 次锁 24 小时。
 * 计数只在**登录成功**时清零（密码不正确不会清零，故无法通过「失败几次再猜」重置）。
 *
 * <p><b>用户名归一化</b>：计数键统一 trim + 转小写 —— 库侧用户名比较不区分大小写，
 * 不归一化会让 {@code Admin} 与 {@code admin} 各记一份计数而绕过锁定。
 *
 * <p><b>存储</b>：进程内内存，与 {@code IpRateLimiter} 同口径（单容器部署，重启清零可接受）；
 * <b>扩到 ≥2 实例前须随限频一并下沉共享存储</b>，否则各实例各记各的，锁定被放大 N 倍。
 */
@Slf4j
@Component
public class LoginAttemptGuard {

    /** 渐进锁定阈值（降序：命中最高档即停） */
    private static final int[] LOCK_THRESHOLDS = {20, 10, 5};

    /** 与 {@link #LOCK_THRESHOLDS} 一一对应的锁定时长（毫秒）：24h / 1h / 5min */
    private static final long[] LOCK_MILLIS = {24 * 60 * 60_000L, 60 * 60_000L, 5 * 60_000L};

    /** 触发告警的失败次数（与最低锁定阈值一致：一旦开始锁人，运维必须知道） */
    public static final int ALERT_THRESHOLD = 5;

    /** 退避起点：自第 3 次失败起开始人为延迟 */
    private static final int BACKOFF_START_FAILURES = 3;

    /** 退避步长与封顶 */
    private static final long BACKOFF_STEP_MS = 500L;

    private static final long BACKOFF_MAX_MS = 3_000L;

    /** 内存兜底容量上限 */
    private static final int MAX_ENTRIES = 10_000;

    /** 条目陈旧判定：超过该时长未再失败即视为可清理（锁定上限 24h） */
    private static final long STATE_TTL_MS = 24 * 60 * 60_000L;

    /** 单个用户名的失败状态 */
    private static final class State {
        private int failures;
        private long lockedUntil;
        private long updatedAt;
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();

    /**
     * 剩余锁定秒数。
     *
     * @return 0 = 未锁定；&gt;0 = 仍需等待的秒数（向上取整）
     */
    public long lockedSeconds(String username) {
        State state = stateOf(username);
        if (state == null) {
            return 0L;
        }
        synchronized (state) {
            long remain = state.lockedUntil - System.currentTimeMillis();
            return remain <= 0 ? 0L : (remain + 999) / 1000;
        }
    }

    /**
     * 本次登录应施加的人为延迟（毫秒）：自第 3 次连续失败起每次 +500ms，封顶 3s。
     * <p>
     * 目的是让「少量尝试」也不经济 —— 攻击者无法以高速率试探口令。
     *
     * @return 0 = 无需延迟
     */
    public long backoffMillis(String username) {
        State state = stateOf(username);
        if (state == null) {
            return 0L;
        }
        synchronized (state) {
            if (state.failures < BACKOFF_START_FAILURES) {
                return 0L;
            }
            return Math.min(BACKOFF_MAX_MS, (state.failures - BACKOFF_START_FAILURES + 1) * BACKOFF_STEP_MS);
        }
    }

    /**
     * 记录一次登录失败，并按梯度刷新锁定截止时间。
     *
     * @return 该用户名累计失败次数
     */
    public int recordFailure(String username) {
        if (!StringUtils.hasText(username)) {
            return 0;
        }
        if (states.size() > MAX_ENTRIES) {
            cleanup();
        }
        State state = states.computeIfAbsent(key(username), k -> new State());
        synchronized (state) {
            state.failures++;
            state.updatedAt = System.currentTimeMillis();
            for (int i = 0; i < LOCK_THRESHOLDS.length; i++) {
                if (state.failures >= LOCK_THRESHOLDS[i]) {
                    state.lockedUntil = Math.max(state.lockedUntil, state.updatedAt + LOCK_MILLIS[i]);
                    break;
                }
            }
            return state.failures;
        }
    }

    /** 登录成功：清零该用户名的失败计数与锁定（不误伤本人） */
    public void clear(String username) {
        if (StringUtils.hasText(username)) {
            states.remove(key(username));
        }
    }

    /** 定时清理陈旧条目（与 {@code IpRateLimiter} 同节奏），防内存缓慢增长 */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        states.entrySet().removeIf(e -> {
            State state = e.getValue();
            synchronized (state) {
                return now - state.updatedAt > STATE_TTL_MS;
            }
        });
    }

    private State stateOf(String username) {
        return StringUtils.hasText(username) ? states.get(key(username)) : null;
    }

    private static String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
