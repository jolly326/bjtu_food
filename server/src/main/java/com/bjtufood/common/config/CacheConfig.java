package com.bjtufood.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 推荐位读接口缓存配置（Caffeine 进程内缓存，P1-9）。
 * <p>
 * 覆盖首页/发现页四个高频读接口：热门菜品、猜你喜欢、热搜词条、新晋黑马。
 * 统一 60 秒写入后过期——推荐位允许分钟级陈旧（对应红线「Caffeine 60s TTL + 写失效」），
 * 写操作（菜品增删改、审核状态变更、评分重算）通过 {@code @CacheEvict} 在写库之后清空对应缓存。
 * <p>
 * 缓存名集中在常量中维护，evict 侧引用 {@link #READ_CACHE_NAMES} 全量失效，
 * 避免读写两侧 cacheNames 漂移。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 热门菜品（/dishes/hot） */
    public static final String CACHE_DISH_HOT = "dishHot";

    /** 猜你喜欢（/dishes/recommend） */
    public static final String CACHE_DISH_RECOMMEND = "dishRecommend";

    /** 热搜词条（/dishes/hot-search） */
    public static final String CACHE_DISH_HOT_SEARCH = "dishHotSearch";

    /** 新晋黑马（/dishes/rising） */
    public static final String CACHE_DISH_RISING = "dishRising";

    /**
     * 全部读缓存名（运行时引用）：写操作失效时全量清空（写低频，全清成本可接受且最稳妥）。
     * 注意：Java 注解元素的值必须是编译期常量，数组字段不能直接用于 {@code @CacheEvict.cacheNames}，
     * evict 处需逐个引用下方四个 String 常量组成的数组字面量。
     */
    public static final String[] READ_CACHE_NAMES = {
            CACHE_DISH_HOT, CACHE_DISH_RECOMMEND, CACHE_DISH_HOT_SEARCH, CACHE_DISH_RISING
    };

    /**
     * 60 秒写入后过期；单缓存最多 1000 条目——recommend 的参数组合
     * （page/pageSize/excludeIds/userId）理论无限，容量上限防止任意参数刷爆内存。
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
