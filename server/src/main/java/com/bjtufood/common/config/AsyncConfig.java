package com.bjtufood.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务执行器配置。
 * <p>
 * 提供名为 {@code taskExecutor} 的线程池 Bean，供 {@code @Async("taskExecutor")} 使用
 * （如 {@code RatingUpdateListener} 评分聚合、{@code NotificationServiceImpl} 站内通知）。相比默认的
 * SimpleAsyncTaskExecutor（每次任务新建线程、无上限），本配置提供有界队列 + 拒绝策略，
 * 避免上述异步任务在流量高峰时无限制创建线程导致 OOM。
 * <p>
 * <b>拒绝策略选 CallerRunsPolicy 的连带约束（D2）</b>：该策略下队列打满时任务
 * <b>在调用方线程同步执行</b>。因此它隐含一个要求：<b>异步方法内的事务传播级别不得是
 * {@code REQUIRES_NEW}</b>——否则在调用方已处于事务中时（如 {@code FeedbackServiceImpl#handle}），
 * 会挂起外层事务再新开一条，单请求峰值占用翻倍（HikariCP 池上限 20，见 {@code application.yml}），
 * 并发写叠加队列打满即可能连接池自锁。
 * {@code NotificationServiceImpl#notify} 原正是这个组合，现已改回默认 {@code REQUIRED}：
 * 该场景下任务并入调用方事务，只占 1 条连接。
 * <p>
 * 若日后需换成 {@code AbortPolicy}（拒绝时抛异常、由调用方感知），则需同步检查各异步方法的
 * 传播级别与调用方的异常处理，不能只改本文件。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(128);
        executor.setThreadNamePrefix("bjtu-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
