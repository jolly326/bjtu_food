package com.bjtufood.auth.support;

import com.bjtufood.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 邮箱验证码「连续失败 → 短期封禁」护栏（进程内实现）。
 * <p>
 * <b>为什么需要它</b>：{@code POST /auth/verify-email} 的凭据是 6 位数字（码空间 10⁶），而校验是
 * <b>按 purpose 全局匹配</b>（{@code AuthServiceImpl#consumeVerifyCodeAndGetEmail}）——
 * 因为发码接口 {@code POST /auth/email-code} 是 permitAll 的匿名接口，服务端**没有任何「谁申请了这条码」
 * 的归属信息**可用来收窄匹配范围。于是这条链路的安全性完全落在「6 位码猜不中」这一点上：
 * 一旦允许无限制尝试，攻击者即可枚举，命中他人未过期验证码后把**别人的学号邮箱绑到自己的微信**上
 * （并触发归属迁移）。本类是让这种枚举不成立的两道闸之一（另一道是 IP 维度限频，见 {@code AuthController}）。
 * <p>
 * <b>口径</b>：
 * <ul>
 *   <li>同一 userId 在 {@link #WINDOW_MS} 窗口内失败满 {@link #MAX_FAILURES} 次 → 窗口结束前一律
 *       fail-fast 拒绝（不再进入 BCrypt 比对）；窗口结束自动恢复，无需人工解锁；</li>
 *   <li>校验成功后清零：正常用户偶发输错不会被累积成封禁；</li>
 *   <li>阈值刻意比「纯防爆破」更宽松（10 次 / 15 分钟）——过期码、手误等合法失败不应立刻锁人；
 *       而 15 分钟内 10 次尝试相对 10⁶ 码空间已足够低，且 IP 维度另有 5 次/分钟、20 次/小时两道闸。</li>
 * </ul>
 * <p>
 * <b>部署局限</b>（与 {@code TokenBlacklist} 同口径）：进程内存储，多实例部署时各实例计数独立
 * （攻击者轮询实例可放大尝试次数），重启即清零。若需严格一致，应下沉共享存储（Redis 计数 + TTL）；
 * 当前单容器云托管部署下该偏差可接受。
 */
@Slf4j
@Component
public class VerifyCodeAttemptGuard {

    /** 触发封禁的窗口内失败次数（public：供 Swagger 文案与跨包单测引用同一真源） */
    public static final int MAX_FAILURES = 10;

    /** 失败计数窗口 = 封禁时长（窗口结束即自动恢复） */
    static final long WINDOW_MS = 15L * 60 * 1000;

    /** 内存兜底容量上限，超过时先做一次清理，避免异常流量下无界增长 */
    private static final int MAX_ENTRIES = 10_000;

    /** userId -> 该窗口内的失败次数与窗口起点 */
    private final Map<Long, Attempt> attempts = new ConcurrentHashMap<>();

    /** 时钟（可注入，供单测推进时间） */
    private final LongSupplier clock;

    public VerifyCodeAttemptGuard() {
        this(System::currentTimeMillis);
    }

    /** 包内可见：单测注入受控时钟 */
    VerifyCodeAttemptGuard(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * 校验前置闸门：处于封禁窗口内直接拒绝，不消耗后续的 BCrypt 比对成本。
     *
     * @param userId 当前登录用户（null 表示匿名，由 {@code verifyEmail} 的 401 前置拦截，本方法直接放行）
     * @throws BusinessException 封禁未结束（message 含剩余等待秒数）
     */
    public void assertNotLocked(Long userId) {
        if (userId == null) {
            return;
        }
        Attempt attempt = attempts.get(userId);
        if (attempt == null) {
            return;
        }
        long now = clock.getAsLong();
        if (isExpired(attempt, now)) {
            attempts.remove(userId, attempt);
            return;
        }
        if (attempt.failures() >= MAX_FAILURES) {
            throw new BusinessException("验证码错误次数过多，请 " + remainingSeconds(attempt, now) + " 秒后再试");
        }
    }

    /** 记录一次校验失败；达到阈值即记 WARN 告警（供发现爆破） */
    public void recordFailure(Long userId) {
        if (userId == null) {
            return;
        }
        if (attempts.size() > MAX_ENTRIES) {
            cleanup();
        }
        long now = clock.getAsLong();
        Attempt attempt = attempts.compute(userId, (key, old) ->
                (old == null || isExpired(old, now)) ? new Attempt(1, now) : old.next());
        if (attempt.failures() >= MAX_FAILURES) {
            log.warn("[ALERT] 验证码连续校验失败已达 {} 次，userId={} 将被封禁 {} 秒",
                    MAX_FAILURES, userId, remainingSeconds(attempt, now));
        }
    }

    /** 校验成功：清零该用户的失败窗口 */
    public void recordSuccess(Long userId) {
        if (userId == null) {
            return;
        }
        attempts.remove(userId);
    }

    /** 该用户当前是否处于封禁窗口（public：供线上排查与跨包单测读取） */
    public boolean isLocked(Long userId) {
        if (userId == null) {
            return false;
        }
        Attempt attempt = attempts.get(userId);
        return attempt != null && !isExpired(attempt, clock.getAsLong())
                && attempt.failures() >= MAX_FAILURES;
    }

    /** 每分钟清理已出窗口的计数（与 {@code TokenBlacklist} 同节奏） */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long now = clock.getAsLong();
        attempts.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    private static boolean isExpired(Attempt attempt, long now) {
        return now - attempt.windowStartMs() > WINDOW_MS;
    }

    private static long remainingSeconds(Attempt attempt, long now) {
        long remainingMs = attempt.windowStartMs() + WINDOW_MS - now;
        return Math.max(1, (remainingMs + 999) / 1000);
    }

    /** 单个用户的失败窗口（窗口起点固定，不因后续失败而顺延 ⇒ 封禁有确定上限） */
    private record Attempt(int failures, long windowStartMs) {
        Attempt next() {
            return new Attempt(failures + 1, windowStartMs);
        }
    }
}
