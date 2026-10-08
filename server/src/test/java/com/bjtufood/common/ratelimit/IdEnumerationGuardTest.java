package com.bjtufood.common.ratelimit;

import com.bjtufood.common.alert.AlertType;
import com.bjtufood.common.alert.SecurityAlertNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * ID 枚举 / 蜜罐探针回归。
 *
 * <p>锁定两条判据：① 命中不存在的资源过密 ⇒ 限流 + 告警；② 顺序递增遍历 ⇒ 限流 + 告警。
 * 同时锁定「正常浏览不受影响」这一反向证据 —— 检测机制最容易犯的错是误伤真实用户。
 */
class IdEnumerationGuardTest {

    private final SecurityAlertNotifier notifier = mock(SecurityAlertNotifier.class);

    private final IdEnumerationGuard guard = new IdEnumerationGuard(notifier);

    @Test
    @DisplayName("正常浏览（少量命中 + 跳着看）不触发任何限流")
    void normalBrowsingIsNotFlagged() {
        for (long id : new long[]{3L, 9L, 4L, 27L, 5L}) {
            guard.recordAccess("1.2.3.4", id);
        }
        guard.recordNotFound("1.2.3.4", 999L);
        guard.recordNotFound("1.2.3.4", 1000L);

        assertThat(guard.blockedSeconds("1.2.3.4")).isZero();
    }

    @Test
    @DisplayName("蜜罐探针：连续命中不存在的 ID 过密 ⇒ 临时限流 + 告警")
    void probeFloodIsFlagged() {
        for (long id = 100L; id < 115L; id++) {
            guard.recordNotFound("5.6.7.8", id);
        }

        assertThat(guard.blockedSeconds("5.6.7.8")).isPositive();
        verify(notifier).notify(eq(AlertType.CRAWL_DETECTED), contains("ID 枚举"), any(String.class));
    }

    @Test
    @DisplayName("顺序遍历：即使资源都存在，按 id 递增过密同样触发")
    void ascendingScanIsFlagged() {
        for (long id = 1L; id <= 40L; id++) {
            guard.recordAccess("9.9.9.9", id);
        }

        assertThat(guard.blockedSeconds("9.9.9.9")).isPositive();
        verify(notifier).notify(any(AlertType.class), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("限流按来源隔离：一个来源被限流不影响其他来源")
    void flagIsPerSource() {
        for (long id = 100L; id < 115L; id++) {
            guard.recordNotFound("bad-source", id);
        }

        assertThat(guard.blockedSeconds("bad-source")).isPositive();
        assertThat(guard.blockedSeconds("good-source")).isZero();
        assertThat(guard.blockedSeconds(null)).isZero();
    }

    @Test
    @DisplayName("非法入参（空来源 / 非正 ID）不计入，避免被构造请求污染计数")
    void invalidInputIsIgnored() {
        guard.recordNotFound(null, 1L);
        guard.recordAccess("1.1.1.1", null);
        guard.recordAccess("1.1.1.1", 0L);

        assertThat(guard.blockedSeconds("1.1.1.1")).isZero();
    }
}
