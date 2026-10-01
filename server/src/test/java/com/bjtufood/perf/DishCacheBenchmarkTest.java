package com.bjtufood.perf;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.config.CacheConfig;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.dish.constant.DishConst;
import com.bjtufood.dish.dto.DishAttributeEditVO;
import com.bjtufood.dish.view.DishViewVO;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.entity.DishAttributeDimension;
import com.bjtufood.dish.mapper.DishAttributeDimensionMapper;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.service.DishAttributeCatalog;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.dish.service.impl.DishServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.AnnotationCacheOperationSource;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * P1 缓存化后的<b>同口径</b>复测：与 {@link DishReadPathBenchmarkTest} 一一对照。
 * <p>
 * <b>为什么能不用 Spring 上下文就测到真实缓存</b>：{@code @Cacheable} 的语义由
 * {@link CacheInterceptor} 这一段 AOP advice 实现，Spring 上下文只是把它接到 bean 上。
 * 这里用 {@link ProxyFactory} 把真实 {@link DishAttributeCatalog} 包上同一段 advice +
 * {@link CacheConfig#buildCacheManager()}（<b>与生产同一份 TTL/容量装配</b>，不是测试里另搭一套），
 * 于是缓存命中、失效、命中率统计全都是真的，只有数据库仍是 mock。
 * <p>
 * <b>本类同时是回归护栏</b>：下面几条断言会在缓存「静默失效」时立刻红掉——声明式缓存最坏的失败模式
 * 不是报错，而是<b>不报错但没生效</b>（自调用不过代理、注解打错位置、缓存名拼错退化成默认缓存），
 * 只看业务单测永远发现不了，只有对着 Mapper 调用次数断言才能抓住。
 * <p>
 * 数值与基线的对应关系见 docs/perf/perf-00-度量口径与基线.md §6。
 */
@DisplayName("P1 缓存化复测（同口径对照基线 + 缓存真实生效的回归护栏）")
class DishCacheBenchmarkTest {

    /** 与基线一致的合成行数：只有同规模，冷热两侧的数值才可直接相减 */
    private static final int ON_SALE_ROWS = 5_000;

    /** 重复调用次数：第 1 次冷（含聚合），其余热（应全部命中缓存） */
    private static final int CALLS = 5;

    private static final List<DishAttributeDimension> DIMENSIONS = List.of(
            dimension(1L, "dietType", "饮食属性", "single", 1),
            dimension(2L, "spiceLevel", "辣度", "single", 2),
            dimension(3L, "flavorTags", "口味", "multi", 3),
            dimension(4L, "serveTemp", "出餐温度", "single", 4));

    private DishMapper dishMapper;
    private DishAttributeDimensionMapper dimensionMapper;
    private DishAttributeCatalog catalog;
    private CacheManager cacheManager;
    private DishService dishService;

    /** 纯 Mockito 无 Spring 上下文，MyBatis-Plus 的 lambda 缓存需显式初始化 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), DishCacheBenchmarkTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, Dish.class);
        TableInfoHelper.initTableInfo(assistant, DishAttributeDimension.class);
    }

    @BeforeEach
    void setUp() {
        dishMapper = mock(DishMapper.class);
        dimensionMapper = mock(DishAttributeDimensionMapper.class);
        when(dimensionMapper.selectList(any())).thenReturn(DIMENSIONS);
        when(dishMapper.selectAttributesJsonOnSale()).thenReturn(onSaleAttributesJson(ON_SALE_ROWS));
        when(dishMapper.selectById(1L)).thenReturn(onSaleDish(sampleAttributesJson(0)));
        when(dishMapper.selectInStockMealTypes()).thenReturn(List.of("staple", "dish"));

        cacheManager = CacheConfig.buildCacheManager();
        DishAttributeCatalog target = new DishAttributeCatalog(dishMapper, dimensionMapper, cacheManager);
        // 手工挂上生产同款的缓存 advice。注意必须显式 afterPropertiesSet()：CacheInterceptor 是
        // InitializingBean，缓存解析器（cacheResolvers）在该回调里构建——不经 Spring 生命周期手工 new 时
        // 若漏掉这一步，它会**安静地什么都不缓存**（不报错、不降速，只有命中率是零），
        // 这正是声明式缓存最典型的静默失效。本类的断言就是为抓住这类错误而写。
        CacheInterceptor interceptor = new CacheInterceptor();
        interceptor.setCacheManager(cacheManager);
        // 注解解析源必须显式给出（CacheInterceptor 是手工 new 的，不经 @EnableCaching 的自动装配）。
        // 注意 Spring 6.1 里这个类在 cache.annotation 包，不在 cache.interceptor 包——名字相近但不同包。
        interceptor.setCacheOperationSource(new AnnotationCacheOperationSource());
        // 手工装配声明式缓存必须自己补两个容器回调，少一个就整层**静默失效**（不报错、不降速，只有命中率是零）：
        //   1) afterPropertiesSet()：Spring 6.1 里它只校验 cacheOperationSource 是否已设置；
        //   2) afterSingletonsInstantiated()：真正把 CacheAspectSupport.initialized 置 true 的地方
        //      （SmartInitializingSingleton 回调，容器在单例实例化完成后必然调用）。
        // execute() 的首行就是 if (!initialized) → 直接旁路缓存、照常执行业务方法：
        // 现象是「advice 挂上了、注解解析出来了、缓存条目却永远是 0」，业务单测全绿，只能靠 Mapper 次数断言抓住。
        // 生产走 @EnableCaching 由容器接管这两个回调，只有脱离容器手工挂 advice（如本类）才需要显式补齐。
        interceptor.afterPropertiesSet();
        interceptor.afterSingletonsInstantiated();
        // 目标类而非接口：DishServiceImpl 注入的是具体类型，生产对该 bean 走的也是 CGLIB 子类代理
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        catalog = (DishAttributeCatalog) factory.getProxy();

        // service 同样要过代理：listDishViews 的 @Cacheable 落在它身上，
        // 直接 new 出来的实例上注解同样惰性无效。
        ProxyFactory serviceFactory = new ProxyFactory(new DishServiceImpl(dishMapper, mock(StallService.class),
                mock(ApplicationEventPublisher.class), mock(ImageUrlUtil.class), catalog));
        serviceFactory.setInterfaces(DishService.class);
        serviceFactory.addAdvice(interceptor);
        dishService = (DishService) serviceFactory.getProxy();
    }

    @Test
    @DisplayName("编辑弹层：缓存生效后热路径单请求 Mapper 调用降到 1，耗时近乎归零")
    void attributesEditHitsCache() {
        // 冷：单次取行 + 维度字典 + 全库候选聚合
        List<DishAttributeEditVO> cold = dishService.listDishAttributes(1L);
        long coldCalls = countCalls();
        assertThat(cold).hasSize(4);

        long before = countCalls();
        long warmNanos = 0L;
        List<DishAttributeEditVO> warm = null;
        for (int i = 0; i < CALLS - 1; i++) {
            long start = System.nanoTime();
            warm = dishService.listDishAttributes(1L);
            warmNanos += System.nanoTime() - start;
        }
        double perRequestCalls = (countCalls() - before) / (double) (CALLS - 1);

        PerfMetrics.emit("server.mapper_calls.dish_attributes_edit_cached", perRequestCalls, "次/请求",
                "热路径（第 2~" + CALLS + " 次）；基线=3（单次取行 + 维度字典 + 全库扫描）；冷路径仍为 " + coldCalls);
        PerfMetrics.emit("server.dish_attributes_edit.latency_ms_warm_cached",
                warmNanos / 1_000_000.0 / (CALLS - 1), "ms",
                "同参数重复调用均值；基线 warm≈14ms（无缓存时热=冷）");

        // 护栏①：缓存不得改变结果（同一份数据、同一顺序）
        assertThat(warm).usingRecursiveComparison().isEqualTo(cold);
        // 护栏②：热路径真的只剩一次取行 → 维度字典与候选聚合都命中了
        assertThat(perRequestCalls).isEqualTo(1.0);
    }

    @Test
    @DisplayName("首页视图字典：第二次进首页不再查库（Mapper 增量=0）")
    void dishViewsHitCache() {
        List<DishViewVO> first = dishService.listDishViews();
        long afterFirst = countCalls();

        List<DishViewVO> second = dishService.listDishViews();
        double repeatCalls = countCalls() - afterFirst;

        PerfMetrics.emit("server.mapper_calls.dish_views_cached", repeatCalls, "次/请求",
                "第二次进入首页的 Mapper 增量；基线=1（每次都查一次在售大类集合）");
        // 诊断/护栏：先确认「缓存里真有条目」，再谈增量——若此处为 0，说明 @Cacheable 根本没接上，
        // 后面的 0 增量断言就没有意义（避免把「装配错误」误读成「性能没提升」）。
        assertThat(cachedEntries(CacheConfig.DISH_VIEWS))
                .as("@Cacheable 未写入缓存：检查 CacheInterceptor 装配与代理是否生效").isEqualTo(1L);
        assertThat(repeatCalls).isZero();
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("写侧显式失效：属性写入后候选值必须重算，命中率被如实记录")
    void invalidationAfterWriteIsEffective() {
        dishService.listDishAttributes(1L);   // 冷：建立缓存
        dishService.listDishAttributes(1L);   // 热：命中
        long before = countCalls();

        catalog.invalidateCandidates();       // 纠错采纳 / 管理端编辑属性走的同一步
        List<DishAttributeEditVO> after = dishService.listDishAttributes(1L);

        long recompute = countCalls() - before;
        PerfMetrics.emit("server.cache.candidates_recompute_after_write", recompute, "次/请求",
                "失效后首次请求的 Mapper 调用（取行 + 候选重算；维度字典仍命中）");
        // 护栏③：失效必须真的生效——否则新写入的取值最长要等一整个 TTL 才出现在候选里
        assertThat(recompute).isEqualTo(2L);
        assertThat(after).hasSize(4);

        CacheStats stats = cacheStats(CacheConfig.ATTRIBUTE_CANDIDATES);
        PerfMetrics.emit("server.cache.attribute_candidates.hit_rate", stats.hitRate() * 100, "%",
                "hits=" + stats.hitCount() + " misses=" + stats.missCount() + "（Caffeine recordStats 实测）");
        assertThat(stats.hitCount()).isPositive();
    }

    private CacheStats cacheStats(String cacheName) {
        CaffeineCache cache = (CaffeineCache) cacheManager.getCache(cacheName);
        Cache<?, ?> nativeCache = cache.getNativeCache();
        return nativeCache.stats();
    }

    /** 缓存内条目数：用于把「@Cacheable 有没有真的写入」从推测变成断言（静默失效的第一现场） */
    private long cachedEntries(String cacheName) {
        return ((CaffeineCache) Objects.requireNonNull(cacheManager.getCache(cacheName)))
                .getNativeCache().estimatedSize();
    }

    private long countCalls() {
        return mockingDetails(dishMapper).getInvocations().size()
                + mockingDetails(dimensionMapper).getInvocations().size();
    }

    private static DishAttributeDimension dimension(Long id, String fieldKey, String name,
                                                    String valueType, Integer order) {
        DishAttributeDimension dim = new DishAttributeDimension();
        dim.setId(id);
        dim.setFieldKey(fieldKey);
        dim.setName(name);
        dim.setValueType(valueType);
        dim.setOrder(order);
        return dim;
    }

    private static Dish onSaleDish(String attributes) {
        Dish dish = new Dish();
        dish.setId(1L);
        dish.setStatus(DishConst.STATUS_ON);
        dish.setAttributes(attributes);
        return dish;
    }

    /** 单菜属性 JSON：4 个维度各一个值（多选维度为数组，与生产落库形态一致） */
    private static String sampleAttributesJson(int seed) {
        return "{\"dietType\":\"v" + (seed % 20) + "\",\"spiceLevel\":\"v" + ((seed + 3) % 20)
                + "\",\"flavorTags\":[\"v" + ((seed + 7) % 20) + "\"],\"serveTemp\":\"v"
                + ((seed + 11) % 20) + "\"}";
    }

    /** 全库在售菜品的 attributes 原文（候选聚合的线性成本据此量化） */
    private static List<String> onSaleAttributesJson(int rows) {
        List<String> list = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            list.add(sampleAttributesJson(i));
        }
        return list;
    }
}

