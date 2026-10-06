package com.bjtufood.auth.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserStateWriteLock} 的并发语义测试。
 * <p>
 * <b>为何必须用真线程而非 mock</b>：本类的全部价值就是「临界区互斥 + 粒度 = userId」，
 * 只有真实并发执行才能体现；断言对象是<b>互斥性</b>本身（对方线程是否进入过临界区），不是调用次数。
 * <p>
 * <b>确定性口径（无偶发）</b>：
 * <ul>
 *   <li>「不得进入」类断言用<b>上界等待断言为假</b>（{@code await(150ms) == false}）——
 *       若互斥失效，对方进入临界区只需微秒级，150ms 有数个数量级余量，故偏离方向只可能是
 *       「本应进入却未进入」（会让「放行后必须进入」的断言失败），不会出现随机红/绿；</li>
 *   <li>「必须进入 / 必须完成」类断言用 2s 上界 —— 正常路径是微秒级完成，2s 只作失败上界。</li>
 * </ul>
 */
class UserStateWriteLockTest {

    /** 「不得进入」的观测上界：互斥失效时对方在微秒级进入，故该上界在数量级上足够 */
    private static final long NOT_ENTERED_WINDOW_MS = 150L;

    @Test
    @DisplayName("同 userId 互斥：持锁线程未退出前，第二个线程不得进入临界区")
    void sameUserIsSerialized() throws Exception {
        UserStateWriteLock writeLock = new UserStateWriteLock();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch firstMayExit = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);

        Thread first = new Thread(() -> writeLock.run(7L, () -> {
            firstEntered.countDown();
            awaitQuietly(firstMayExit);
        }));
        first.start();
        assertThat(firstEntered.await(2, TimeUnit.SECONDS)).isTrue();

        Thread second = new Thread(() -> writeLock.run(7L, secondEntered::countDown));
        second.start();
        assertThat(secondEntered.await(NOT_ENTERED_WINDOW_MS, TimeUnit.MILLISECONDS))
                .as("第一个线程仍在 7L 的临界区内，第二个线程不得进入（互斥）")
                .isFalse();

        firstMayExit.countDown();
        assertThat(secondEntered.await(2, TimeUnit.SECONDS))
                .as("第一个线程退出后，第二个线程必须被放行（不得死锁）")
                .isTrue();

        first.join(2000);
        second.join(2000);
    }

    @Test
    @DisplayName("粒度 = userId：持锁用户 A 期间，用户 B 的临界区照常完成（不得为全局锁）")
    void distinctUsersDoNotBlockEachOther() throws Exception {
        UserStateWriteLock writeLock = new UserStateWriteLock();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch firstMayExit = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);

        Thread first = new Thread(() -> writeLock.run(1L, () -> {
            firstEntered.countDown();
            awaitQuietly(firstMayExit);
        }));
        first.start();
        assertThat(firstEntered.await(2, TimeUnit.SECONDS)).isTrue();

        Thread second = new Thread(() -> writeLock.run(2L, secondDone::countDown));
        second.start();
        assertThat(secondDone.await(2, TimeUnit.SECONDS))
                .as("用户 1 持锁不得阻塞用户 2：单把全局锁会在此超时失败（管理端批量改状态会退化成全站串行）")
                .isTrue();

        firstMayExit.countDown();
        first.join(2000);
        second.join(2000);
    }

    @Test
    @DisplayName("锁条目随临界区结束回收：1000 个不同 userId 跑完后 lock 表为空（不随用户总量增长）")
    void entriesAreReleasedAfterCriticalSection() {
        UserStateWriteLock writeLock = new UserStateWriteLock();

        for (long userId = 1L; userId <= 1000L; userId++) {
            writeLock.run(userId, () -> { });
        }

        assertThat(writeLock.trackedUserCount()).isZero();
    }

    @Test
    @DisplayName("userId 为 null：不加锁直接执行（既有空值语义不受影响）")
    void nullUserIdRunsWithoutLock() {
        UserStateWriteLock writeLock = new UserStateWriteLock();
        AtomicBoolean ran = new AtomicBoolean();

        writeLock.run(null, () -> ran.set(true));

        assertThat(ran).isTrue();
        assertThat(writeLock.trackedUserCount()).isZero();
    }

    /** 等待闩锁（中断时恢复中断位后返回，交由后续断言判定是否真的进入/完成） */
    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
