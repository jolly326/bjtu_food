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
 * （如 {@code RatingUpdateListener} 评分聚合）。相比默认的 SimpleAsyncTaskExecutor
 * （每次任务新建线程、无上限），本配置提供有界队列 + CallerRunsPolicy 拒绝策略，
 * 避免评分重算等异步任务在流量高峰时无限制创建线程导致 OOM。
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
