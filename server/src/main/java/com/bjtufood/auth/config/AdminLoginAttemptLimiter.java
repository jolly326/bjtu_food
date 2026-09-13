package com.bjtufood.auth.config;

import com.bjtufood.common.exception.BusinessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理后台登录失败锁定（进程内内存实现，组件风格对齐本包 TokenBlacklist）。
 * <p>
 * 同一账号 15 分钟内累计 5 次登录失败即锁定 15 分钟，抑制对
 * {@code /auth/admin/login} 的暴力破解尝试。状态仅存 JVM 内存、重启清零
 * （可接受：锁定为攻防止损，不要求跨实例强一致）；设容量上限 + 定时清理，
 * 防恶意刷账号名导致内存泄漏。
 */
@Component
public class AdminLoginAttemptLimiter {

    /** 触发锁定的连续失败次数阈值 */
    private static final int MAX_FAILURES = 5;

    /** 锁定时长：15 分钟 */
    private static final long LOCK_DURATION_MS = 15L * 60 * 1000;

    /** 失败计数的滑动统计窗口：与锁定时长一致（15 分钟内累计才生效，跨窗口重新计） */
    private static final long FAILURE_WINDOW_MS = 15L * 60 * 1000;

    /** 内存兜底容量上限，超过时先做一次清理，避免异常流量下无限增长 */
    private static final int MAX_ENTRIES = 10_000;

    /**
     * 单账号失败记录：累计失败次数 / 锁定截止时间（0=未锁定）/ 最近一次失败时间。
     */
    private record Attempt(int failures, long lockedUntil, long lastFailureAt) {
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * 登录前检查：该账号是否处于锁定窗口内；锁定中直接拒绝（不暴露账号是否存在）。
     */
    public void checkLocked(String account) {
        Attempt a = attempts.get(account);
        long now = System.currentTimeMillis();
        if (a != null && a.lockedUntil() > now) {
            long remainMinutes = (a.lockedUntil() - now + 59_999) / 60_000;
            throw new BusinessException(400, "登录失败次数过多，账号已临时锁定，请约 " + remainMinutes + " 分钟后重试");
        }
    }

    /**
     * 登录失败累计：15 分钟窗口内达到 5 次即锁定 15 分钟；跨窗口自动重新计数。
     */
    public void recordFailure(String account) {
        if (attempts.size() > MAX_ENTRIES) {
            cleanup();
        }
        long now = System.currentTimeMillis();
        attempts.compute(account, (k, prev) -> {
            // 锁定窗口内的失败（正常流程已在 checkLocked 拦截，此处防御性兜底）：顺延锁定
            if (prev != null && prev.lockedUntil() > now) {
                return new Attempt(prev.failures(), now + LOCK_DURATION_MS, now);
            }
            // 上一次失败已滑出统计窗口（或从未失败）：重新计数
            boolean expired = prev == null || now - prev.lastFailureAt() > FAILURE_WINDOW_MS;
            int failures = expired ? 1 : prev.failures() + 1;
            long lockedUntil = failures >= MAX_FAILURES ? now + LOCK_DURATION_MS : 0L;
            return new Attempt(failures, lockedUntil, now);
        });
    }

    /**
     * 登录成功清除该账号的失败记录。
     */
    public void reset(String account) {
        attempts.remove(account);
    }

    /** 每分钟清理无效条目：锁定已解除且失败计数已滑出统计窗口的（与 TokenBlacklist 同节奏） */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e ->
                e.getValue().lockedUntil() <= now
                        && now - e.getValue().lastFailureAt() > FAILURE_WINDOW_MS);
    }
}
