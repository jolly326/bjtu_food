package com.bjtufood.perf;

import com.bjtufood.common.ratelimit.IpRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IpRateLimiter} 吞吐基准：量化「单把全局锁」在并发下的串行化成本。
 * <p>
 * 现实现的 {@code tryAcquire} 用 {@code synchronized (this)} 包住全部键的读改写——
 * 任意两个请求（不同 IP、不同 scope）都不能并行记账，吞吐上限即单线程成本。
 * 本基准用两组真实流量画像把该上限量化：
 * <ol>
 *   <li><b>正常流量</b>：8 线程 × 8000 次、8000 个独立 IP、规则宽松（全部放行）——
 *       衡量公开读写字数下的稳态吞吐；</li>
 *   <li><b>被刷流量</b>：规则为生产口径（每分钟 ≤2 条 + 每小时 ≤10 条），超出配额的请求
 *       走「扫描窗口并返回等待秒数」分支——衡量滥用场景下的吞吐（该分支同样在全局锁内）。</li>
 * </ol>
 * 与 {@link DishReadPathBenchmarkTest} 同一取舍：只记录数值、不断言耗时。
 */
@DisplayName("IP 限流器吞吐基准（全局锁串行化成本）")
class IpRateLimiterBenchmarkTest {

    /** 并发线程数（对齐云托管实例的常见并发度） */
    private static final int THREADS = 8;

    /** 每线程请求次数 */
    private static final int OPS_PER_THREAD = 8_000;

    /** 独立 IP 数：保持在实现的 MAX_ENTRIES(10000) 之下，避免测量途中混入全表清理成本 */
    private static final int KEYS = 8_000;

    @Test
    @DisplayName("正常流量：宽松规则全放行")
    void normalTraffic() {
        measure("server.rate_limiter.throughput_ops_per_sec_normal",
                new IpRateLimiter.Rule(100, 60_000L), "宽松规则（≤100/分钟），全部放行");
    }

    @Test
    @DisplayName("被刷流量：生产口径规则，多数请求被拒")
    void abusiveTraffic() {
        measure("server.rate_limiter.throughput_ops_per_sec_abuse",
                new IpRateLimiter.Rule(2, 60_000L), "生产口径（每分钟 ≤2 条），多数请求走拒绝分支");
    }

    /** 预热一轮（不计入），随后测量一轮并输出吞吐与放行率 */
    private void measure(String metricKey, IpRateLimiter.Rule rule, String note) {
        runRound(new IpRateLimiter(), rule);
        Result result = runRound(new IpRateLimiter(), rule);
        double opsPerSecond = result.ops * 1_000_000_000.0 / result.nanos;
        PerfMetrics.emit(metricKey, Math.round(opsPerSecond), "ops/s",
                note + "；threads=" + THREADS + " ops=" + result.ops + " keys=" + KEYS
                        + " 放行率=" + String.format("%.1f%%", result.admitted * 100.0 / result.ops));
    }

    /** 一轮并发压测：返回总次数、耗时与放行次数 */
    private Result runRound(IpRateLimiter limiter, IpRateLimiter.Rule rule) {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(THREADS);
        AtomicLong admitted = new AtomicLong();
        long total = (long) THREADS * OPS_PER_THREAD;
        try {
            for (int t = 0; t < THREADS; t++) {
                final int threadIndex = t;
                pool.submit(() -> {
                    try {
                        startGate.await();
                        for (int i = 0; i < OPS_PER_THREAD; i++) {
                            long keyIndex = ((long) threadIndex * OPS_PER_THREAD + i) % KEYS;
                            if (limiter.tryAcquire("feedback", "10.0." + (keyIndex / 256) + "." + (keyIndex % 256),
                                    rule) == 0L) {
                                admitted.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }
            long begin = System.nanoTime();
            startGate.countDown();
            assertThat(doneGate.await(60, TimeUnit.SECONDS)).isTrue();
            long elapsed = System.nanoTime() - begin;
            return new Result(total, elapsed, admitted.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("压测线程被打断", e);
        } finally {
            pool.shutdownNow();
        }
    }

    /** 一轮压测结果 */
    private record Result(long ops, long nanos, long admitted) {
    }
}

