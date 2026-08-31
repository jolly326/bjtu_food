package com.bjtufood.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 短 TTL 本地缓存配置（架构决策 D-D / design.md D-4）。
 * <p>
 * 用于社区「推荐 / 热门 / 广场 / 排行榜」等读多写少、允许短暂不一致（≤60s）的发现类列表。
 * 采用 Caffeine 进程内缓存 + Spring Cache 抽象，配合写操作 {@code @CacheEvict} 失效对应键。
 * <p>
 * 注意：本缓存仅缓存「列表型查询结果」（不含单条详情/计数），且写入侧（发布/编辑/删除/赞/评论）
 * 必须显式 {@code @CacheEvict} 清掉对应键，避免旧数据滞留超过 TTL 窗口。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 发现类列表统一 60s TTL（D-D 约定） */
    public static final long DISCOVERY_TTL_SECONDS = 60;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(DISCOVERY_TTL_SECONDS, TimeUnit.SECONDS)
                .maximumSize(500)
                .softValues());
        return manager;
    }
}
