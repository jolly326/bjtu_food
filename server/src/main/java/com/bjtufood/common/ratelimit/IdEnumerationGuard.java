package com.bjtufood.common.ratelimit;

import com.bjtufood.common.alert.AlertType;
import com.bjtufood.common.alert.SecurityAlertNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ID 枚举 / 爬取检测（公开读路径的蜜罐探针）。
 *
 * <p><b>要解决什么</b>：限频只能压「速率」，压不住「慢慢按 id 顺序把全量数据捞走」——
 * 攻击者每分钟只发几次请求，速率闸完全放行，而数据已被抄完。
 *
 * <p><b>两条判据</b>（正常用户都不会触发）：
 * <ul>
 *   <li><b>蜜罐探针</b>：命中「不存在的资源」过密 —— 正常路径（点卡片进详情）几乎不会请求不存在的 id，
 *       短时间连续命中只可能是在**扫描有效 id 区间**；</li>
 *   <li><b>顺序遍历</b>：同一来源对 {@code {id}} 的访问呈**递增**形态（{@code id = 上一个 + 1} 附近）
 *       且过密 —— 正常用户不会按 id 逐条走，这是脚本遍历的典型指纹。</li>
 * </ul>
 *
 * <p><b>处置</b>：命中即**临时限流**（{@value #FLAG_MS} / 1000 秒）+ 推送告警，
 * 到期自动解除（不封账号 —— 误判代价必须可自愈）。
 *
 * <p><b>存储</b>：进程内内存，与 {@code IpRateLimiter} 同口径（重启清零可接受；
 * 扩到 ≥2 实例前须随限频一并下沉共享存储）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdEnumerationGuard {

    /** 窗口长度：统计与判定都在这一个窗口内进行 */
    private static final long WINDOW_MS = 60_000L;

    /** 蜜罐阈值：窗口内「命中不存在的 ID」上限（正常用户≈0） */
    private static final int PROBE_LIMIT = 10;

    /** 顺序遍历阈值：窗口内「递增访问」上限 */
    private static final int ASCENDING_LIMIT = 20;

    /** 递增判定容差：相邻两次访问的 id 差值不超过该值即视为「顺序遍历」 */
    private static final long ASCENDING_STEP = 3L;

    /** 命中后临时限流时长 */
    private static final long FLAG_MS = 5 * 60_000L;

    /** 内存兜底容量上限（异常流量下 key 不无限增长） */
    private static final int MAX_ENTRIES = 10_000;

    /** 条目陈旧判定：超过该时长未再访问即视为可清理 */
    private static final long STATE_TTL_MS = 10 * 60_000L;

    /** 单个来源的访问状态 */
    private static final class State {
        private long windowStart;
        private int probes;
        private int ascending;
        private Long lastId;
        private long flaggedUntil;
        private long updatedAt;
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();

    private final SecurityAlertNotifier securityAlertNotifier;

    /**
     * 该来源是否已被临时限流。
     *
     * @param source 来源标识（IP）
     * @return 剩余限流秒数；0 = 未限流
     */
    public long blockedSeconds(String source) {
        State state = source == null ? null : states.get(source);
        if (state == null) {
            return 0L;
        }
        synchronized (state) {
            long remain = state.flaggedUntil - System.currentTimeMillis();
            return remain <= 0 ? 0L : (remain + 999) / 1000;
        }
    }

    /**
     * 记录一次「命中不存在的公开资源」（蜜罐探针）。
     *
     * @param source 来源标识（IP）
     * @param id     被请求的资源 ID
     */
    public void recordNotFound(String source, Long id) {
        record(source, id, true);
    }

    /**
     * 记录一次公开资源的按 ID 访问（存在性未知时用于顺序遍历判定）。
     *
     * @param source 来源标识（IP）
     * @param id     被请求的资源 ID
     */
    public void recordAccess(String source, Long id) {
        record(source, id, false);
    }

    private void record(String source, Long id, boolean notFound) {
        if (source == null || id == null || id <= 0) {
            return;
        }
        if (states.size() > MAX_ENTRIES) {
            cleanup();
        }
        State state = states.computeIfAbsent(source, k -> new State());
        synchronized (state) {
            long now = System.currentTimeMillis();
            state.updatedAt = now;
            if (now - state.windowStart > WINDOW_MS) {
                state.windowStart = now;
                state.probes = 0;
                state.ascending = 0;
            }
            if (notFound) {
                state.probes++;
            }
            if (state.lastId != null && id > state.lastId && id - state.lastId <= ASCENDING_STEP) {
                state.ascending++;
            }
            state.lastId = id;

            String reason = null;
            if (state.probes > PROBE_LIMIT) {
                reason = "疑似 ID 枚举（连续命中不存在的资源）";
            } else if (state.ascending > ASCENDING_LIMIT) {
                reason = "疑似 ID 枚举（顺序递增遍历）";
            }
            if (reason != null && now >= state.flaggedUntil) {
                state.flaggedUntil = now + FLAG_MS;
                state.probes = 0;
                state.ascending = 0;
                log.warn("[ALERT] {}：source={}，已临时限流 {} 秒", reason, source, FLAG_MS / 1000);
                securityAlertNotifier.notify(AlertType.CRAWL_DETECTED, reason,
                        "来源 IP=" + source + " · 已临时限流 " + (FLAG_MS / 1000) + " 秒");
            }
        }
    }

    /** 定时清理陈旧条目（与 {@code IpRateLimiter} 同节奏），防内存缓慢增长 */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        states.entrySet().removeIf(e -> {
            State state = e.getValue();
            synchronized (state) {
                return now - state.updatedAt > STATE_TTL_MS && now >= state.flaggedUntil;
            }
        });
    }
}
