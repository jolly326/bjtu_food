package com.bjtufood.auth.service.impl;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 按 userId 串行化「账号状态写（DB）+ TokenBlacklist 写」的临界区。
 * <p>
 * <b>为何需要</b>：管理员状态变更（{@code UserServiceImpl#updateStatus}：写 {@code user.status}
 * 后同步 {@link com.bjtufood.auth.config.TokenBlacklist}）与账号注销
 * （{@code AuthServiceImpl#deleteAccount}：写 {@code status='deleted'} 后拉黑）都写<b>同一行</b>的
 * {@code status} 列并写黑名单。两组写若交错，终态可能是「DB 为 {@code active}，而该 userId 仍在拉黑中」——
 * 此时该用户连重新登录换到的新 token 也会被 {@code JwtAuthFilter} 按 userId 判定为已失效而一律 401，
 * 且只能等服务重启（内存黑名单清空）或 7 天窗口过期才自愈。
 * <p>
 * <b>粒度 = userId</b>：不同用户互不阻塞（管理端批量改状态时不会退化成全站串行）。
 * 锁条目引用计数归零即从表中移除，故 lock 表大小只与「并发在途的用户数」相关，与用户总量无关。
 * <p>
 * <b>部署局限</b>（与 {@code TokenBlacklist} 同口径）：进程内锁，只保证单实例内的临界区互斥；
 * 跨实例的一致性需黑名单本身下沉共享存储后才可能成立。
 */
@Component
public class UserStateWriteLock {

    /** userId -> 锁条目（引用计数 + 锁对象）；计数同时涵盖「已在临界区内」与「正在等待进入」的线程 */
    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();

    /**
     * 在 {@code userId} 的临界区内执行 {@code action}；{@code action} 抛出的异常原样向外传播。
     * <p>
     * {@code userId} 为 {@code null} 时无可串行的对象，直接执行（调用方既有的空值语义不受影响，
     * 如 {@code updateStatus} 的 4001「用户不存在」、{@code deleteAccount} 的 401「请先登录」）。
     */
    public void run(Long userId, Runnable action) {
        if (userId == null) {
            action.run();
            return;
        }
        Entry entry = acquire(userId);
        entry.lock.lock();
        try {
            action.run();
        } finally {
            // 必须先递减再 unlock：若先 unlock，等待线程可能已持锁进入临界区，而本线程的递减会把条目摘除，
            // 使其后到达的线程拿到「新的锁对象」并与在途线程并行进入临界区。
            release(userId);
            entry.lock.unlock();
        }
    }

    /** 当前仍被跟踪的 userId 数（临界区结束后即回收）；仅供测试断言锁表不随用户总量增长 */
    int trackedUserCount() {
        return entries.size();
    }

    /** 登记一次进入；计数涵盖等待线程，避免其持锁期间条目被在途线程摘除 */
    private Entry acquire(Long userId) {
        return entries.compute(userId, (id, exist) -> {
            Entry entry = exist != null ? exist : new Entry();
            entry.holders++;
            return entry;
        });
    }

    /** 注销一次进入；计数归零即回收条目 */
    private void release(Long userId) {
        entries.computeIfPresent(userId, (id, entry) -> --entry.holders == 0 ? null : entry);
    }

    /** 单个 userId 的锁条目：{@code holders} 只在 {@link ConcurrentHashMap} 的 compute 内变更，故无需额外同步 */
    private static final class Entry {
        private final ReentrantLock lock = new ReentrantLock();
        private int holders;
    }
}
