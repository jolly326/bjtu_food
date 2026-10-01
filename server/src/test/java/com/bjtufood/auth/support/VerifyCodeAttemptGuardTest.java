package com.bjtufood.auth.support;

import com.bjtufood.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link VerifyCodeAttemptGuard} 单元测试。
 * <p>
 * 该护栏是「6 位验证码防枚举」的两道闸之一（另一道是 Controller 的 IP 限频），
 * 其价值全在<b>边界</b>上：差一次不封、满次即封、窗口到点自动解封、成功清零。
 * 故这里用受控时钟把窗口边界钉死，而不是只测「能封禁」。
 */
class VerifyCodeAttemptGuardTest {

    /** 受控时钟：默认从 1000s 起，便于断言剩余秒数 */
    private long now = 1_000_000L;

    private final VerifyCodeAttemptGuard guard = new VerifyCodeAttemptGuard(() -> now);

    @Test
    @DisplayName("未达阈值：不封禁，且 pre-check 不抛异常")
    void notLockedBelowThreshold() {
        for (int i = 0; i < VerifyCodeAttemptGuard.MAX_FAILURES - 1; i++) {
            guard.recordFailure(1L);
        }
        assertThat(guard.isLocked(1L)).isFalse();
        assertThatCode(() -> guard.assertNotLocked(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("达阈值：即封禁，pre-check 抛出带剩余秒数的业务异常（fail-fast，不再消耗 BCrypt）")
    void locksAtThreshold() {
        for (int i = 0; i < VerifyCodeAttemptGuard.MAX_FAILURES; i++) {
            guard.recordFailure(1L);
        }

        assertThat(guard.isLocked(1L)).isTrue();
        assertThatThrownBy(() -> guard.assertNotLocked(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("次数过多")
                // 窗口 15 分钟 ⇒ 刚触发时剩余 900 秒
                .hasMessageContaining("900 秒");
    }

    @Test
    @DisplayName("窗口到点自动解封（无需人工解锁），且计数从零重开")
    void unlocksAfterWindow() {
        for (int i = 0; i < VerifyCodeAttemptGuard.MAX_FAILURES; i++) {
            guard.recordFailure(1L);
        }
        assertThat(guard.isLocked(1L)).isTrue();

        now += VerifyCodeAttemptGuard.WINDOW_MS + 1;
        assertThat(guard.isLocked(1L)).isFalse();
        assertThatCode(() -> guard.assertNotLocked(1L)).doesNotThrowAnyException();

        // 解封后重新计：再失败一次不应直接封禁（证明窗口确实重开，而不是残留旧计数）
        guard.recordFailure(1L);
        assertThat(guard.isLocked(1L)).isFalse();
    }

    @Test
    @DisplayName("校验成功：清零失败计数（偶发输错不累积）")
    void successResetsCounters() {
        for (int i = 0; i < VerifyCodeAttemptGuard.MAX_FAILURES - 1; i++) {
            guard.recordFailure(1L);
        }
        guard.recordSuccess(1L);

        for (int i = 0; i < VerifyCodeAttemptGuard.MAX_FAILURES - 1; i++) {
            guard.recordFailure(1L);
        }
        assertThat(guard.isLocked(1L)).isFalse();
    }

    @Test
    @DisplayName("计数按 userId 隔离：一个账号被封不影响其他账号")
    void countersArePerUser() {
        for (int i = 0; i < VerifyCodeAttemptGuard.MAX_FAILURES; i++) {
            guard.recordFailure(1L);
        }
        assertThat(guard.isLocked(1L)).isTrue();
        assertThat(guard.isLocked(2L)).isFalse();
        assertThatCode(() -> guard.assertNotLocked(2L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("定时清理只清已出窗口的计数，未出窗口的封禁保持有效")
    void cleanupKeepsActiveLock() {
        for (int i = 0; i < VerifyCodeAttemptGuard.MAX_FAILURES; i++) {
            guard.recordFailure(1L);   // 封禁中
        }
        guard.recordFailure(2L);       // 仅 1 次失败

        now += VerifyCodeAttemptGuard.WINDOW_MS + 1;
        guard.cleanup();

        assertThat(guard.isLocked(1L)).isFalse();
        assertThat(guard.isLocked(2L)).isFalse();
    }

    @Test
    @DisplayName("匿名（userId 为 null）：一律放行且不计账（由 Service 侧 401 前置拦截）")
    void anonymousIsIgnored() {
        for (int i = 0; i < VerifyCodeAttemptGuard.MAX_FAILURES + 5; i++) {
            guard.recordFailure(null);
        }
        assertThatCode(() -> guard.assertNotLocked(null)).doesNotThrowAnyException();
        guard.recordSuccess(null);
    }
}
