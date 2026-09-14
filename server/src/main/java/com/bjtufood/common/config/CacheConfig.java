package com.bjtufood.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 读接口缓存配置（Caffeine 进程内缓存，P1-9）。
 * <p>
 * 2026-09-14 §7.10 治理后仅剩热搜词条一个推荐位读接口：
 * 热门菜品（/dishes/hot）、猜你喜欢（/dishes/recommend）、新晋黑马（/dishes/rising）三个端点随端上零消费一并下线，
 * 对应缓存常量 {@code CACHE_DISH_HOT} / {@code CACHE_DISH_RECOMMEND} / {@code CACHE_DISH_RISING} 已零引用删除。
 * <p>
 * 统一 60 秒写入后过期——推荐位允许分钟级陈旧（对应红线「Caffeine 60s TTL + 写失效」），
 * 写操作（菜品增删改、审核状态变更、评分重算）通过 {@code @CacheEvict} 在写库之后清空该缓存。
 * <p>
 * 缓存名集中在常量中维护，evict 侧引用同一常量，避免读写两侧 cacheNames 漂移。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 热搜词条（/dishes/hot-search） */
    public static final String CACHE_DISH_HOT_SEARCH = "dishHotSearch";

    /**
     * 全部读缓存名（运行时引用）。
     * 注意：Java 注解元素的值必须是编译期常量，数组字段不能直接用于 {@code @CacheEvict.cacheNames}，
     * evict 处需逐个引用上方常量组成的数组字面量。
     */
    public static final String[] READ_CACHE_NAMES = {
            CACHE_DISH_HOT_SEARCH
    };

    /**
     * 60 秒写入后过期；单缓存最多 1000 条目（容量上限防止任意参数刷爆内存）。
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(READ_CACHE_NAMES);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(1_000));
        return manager;
    }
}
