package com.bjtufood.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 读路径缓存（P1）。
 * <p>
 * <b>恢复登记</b>：本类随「推荐/热门缓存」整包删除（{@code CacheConfig} +
 * {@code @EnableCaching} + Caffeine 依赖同批删除，留档见 {@code server/pom.xml}）。
 * 本次按该留档「如需恢复读缓存，须重新论证并登记」的要求恢复：论证与登记就写在
 * {@code server/pom.xml} 的同一条留档注释里（重建<u>范围仅限字典/聚合读结果</u>，
 * 「猜你喜欢」等随机内容仍不缓存——该理由针对的正是后者）。
 * <p>
 * <b>缓存对象的选取标准</b>：只缓存「全站共享 + 变化极慢 + 重算昂贵」三者兼备的读结果。
 * 本次三个缓存全部来自基线（docs/perf/perf-00-度量口径与基线.md §3）里的两条热路径：
 * <ul>
 *   <li>{@link #ATTRIBUTE_CANDIDATES}：编辑弹层候选值 = <b>全库在售菜品 attributes 逐行 JSON 解析</b>
 *       （5000 行合成样本实测 ~14ms/次，且线上无缓存 ⇒ 热路径 = 冷路径）；</li>
 *   <li>{@link #ATTRIBUTE_DIMENSIONS}：维度字典，应用内<b>没有写入口</b>（由建表种子维护），
 *       所以敢给到 10 分钟；</li>
 *   <li>{@link #DISH_VIEWS}：首页筛选视图所依赖的「在售大类集合」，每次进首页都要查一次。</li>
 * </ul>
 * <p>
 * <b>刻意不缓存的东西</b>：菜品详情（含 {@code view_count} 自增，缓存会让计数肉眼可见地丢）、
 * 菜品列表（分页 + 随机种子，命中率≈0 且键空间无界）、猜你喜欢（按会话种子随机，
 * 缓存等于把「发现态」在 TTL 内对所有人冻结成同一批菜——那是产品行为变更，不是性能优化）。
 * <p>
 * <b>一致性口径</b>：TTL 上限即最大陈旧窗口，写侧另有显式失效（见
 * {@code DishAttributeCatalog#invalidateCandidates()}）。失效放在写事务提交前触发，
 * 极端并发下可能被一次并发读回填成旧值，其后果由 TTL 兜住——这是「字典类数据」可接受的取舍，
 * 不适用于任何有强一致要求的读。
 * <p>
 * 用 {@link SimpleCacheManager} 而不是 {@code CaffeineCacheManager}：前者<b>不动态建缓存</b>，
 * 拼错缓存名会立刻在日志/测试里暴露，而不是悄悄退化成一个无 TTL 无上限的默认缓存。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 首页筛选视图的「在售大类集合」（key 固定：全量集合本身没有维度） */
    public static final String DISH_VIEWS = "dishViews";

    /** 描述属性维度字典（应用内只读，由建表种子维护） */
    public static final String ATTRIBUTE_DIMENSIONS = "attributeDimensions";

    /** 描述属性编辑候选值（全库 attributes 聚合结果） */
    public static final String ATTRIBUTE_CANDIDATES = "attributeCandidates";

    /**
     * 各缓存的最大陈旧窗口。
     * <p>
     * 2 分钟不是拍脑袋：它是「一次纠错采纳到候选值在管理端可见」的可接受延迟上限，
     * 也是编辑弹层聚合成本（十毫秒级）与 DB 读放大之间的平衡点。
     * 维度字典因应用内无写入口而放宽到 10 分钟。
     */
    private static final Map<String, Duration> TIME_TO_LIVE = Map.of(
            DISH_VIEWS, Duration.ofMinutes(2),
            ATTRIBUTE_DIMENSIONS, Duration.ofMinutes(10),
            ATTRIBUTE_CANDIDATES, Duration.ofMinutes(2));

    /**
     * 单缓存条目上限。
     * <p>
     * 当前三个缓存都只有 1 个 key，上限取 8 是「为将来按视图/按维度分键留位置，
     * 同时给内存一个硬边界」——缓存不该因为键空间扩张而变成内存泄漏点。
     */
    private static final int MAX_ENTRIES_PER_CACHE = 8;

    @Bean
    public CacheManager cacheManager() {
        // 走同一个工厂：基准测试用它在无数据库环境下复现线上缓存语义（见 DishCacheBenchmarkTest）
        return buildCacheManager();
    }

    /** 生产与基准测试共用的缓存装配入口（同一份 TTL / 容量口径，避免「测试里快、线上没这么快」） */
    public static SimpleCacheManager buildCacheManager() {
        List<org.springframework.cache.Cache> caches = new ArrayList<>(TIME_TO_LIVE.size());
        TIME_TO_LIVE.forEach((name, ttl) -> caches.add(new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(MAX_ENTRIES_PER_CACHE)
                .expireAfterWrite(ttl)
                // 记录命中率：/actuator 之外，排障时可直接从缓存对象读到 hit/miss 而不必猜
                .recordStats()
                .build())));
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(caches);
        // 必须显式初始化：Spring 6.1 起 AbstractCacheManager.getCache() 不再惰性初始化，
        // 未 initializeCaches() 的管理器对任何名字都返回 null，而 AbstractCacheResolver 拿到 null
        // 只打一条 debug 日志就跳过——整层缓存会**静默失效**（不报错、不降速、命中率恒为 0）。
        // 生产里这一步本来由容器生命周期回调（afterPropertiesSet）完成，但本方法是「工厂返回即用」
        // 的口径，基准测试也直接用它，所以不能依赖容器帮忙。
        manager.initializeCaches();
        return manager;
    }
}
