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
import com.bjtufood.common.utils.JsonMapUtil;
import com.bjtufood.dish.entity.DishAttributeValue;
import com.bjtufood.dish.entity.DishFilterView;
import com.bjtufood.dish.mapper.DishAttributeValueMapper;
import com.bjtufood.dish.mapper.DishFilterViewMapper;
import com.bjtufood.dish.service.DishViewCatalog;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.mapper.DishViewLogMapper;
import com.bjtufood.dish.service.DishAttributeAdminService;
import com.bjtufood.dish.service.DishCategoryAdminService;
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
import static org.mockito.ArgumentMatchers.anyInt;
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
            dimension(1L, "饮食属性", "single", 1),
            dimension(2L, "辣度", "single", 2),
            dimension(3L, "口味", "multi", 3),
            dimension(4L, "出餐温度", "single", 4));

    private DishMapper dishMapper;
    private DishViewLogMapper dishViewLogMapper;
    private DishAttributeDimensionMapper dimensionMapper;
    /** 取值字典（A4 落地后的**候选值真源**）：声明为字段以便打桩 */
    private DishAttributeValueMapper valueMapper;
    private DishAttributeAdminService attributeAdminService;
    /** 视图目录（A6）：`GET /dishes/views` 的取数与缓存都收在这里 */
    private DishFilterViewMapper viewMapper;
    private DishViewCatalog viewCatalog;
    private DishAttributeCatalog catalog;
    private CacheManager cacheManager;
    private DishService dishService;

    /**
     * 取值字典样本（4 维 × 2~3 个取值）。
     * <p>
     * <b>2026-10-03 口径变更</b>：候选值不再来自「扫全库在售菜品 attributes 按频次去重」，
     * 而是直读本字典（A4 落地）—— 故本类不再需要合成 {@code ON_SALE_ROWS} 行 attributes JSON。
     */
    private static List<DishAttributeValue> attributeValues() {
        return List.of(
                value(10L, 1L, "清淡", 1), value(11L, 1L, "半荤", 2),
                value(20L, 2L, "微辣", 1), value(21L, 2L, "中辣", 2), value(22L, 2L, "重辣", 3),
                value(30L, 3L, "酸", 1), value(31L, 3L, "辣", 2),
                value(40L, 4L, "热食", 1), value(41L, 4L, "冷食", 2));
    }

    private static DishAttributeValue value(Long id, Long dimensionId, String label, int order) {
        DishAttributeValue v = new DishAttributeValue();
        v.setId(id);
        v.setDimensionId(dimensionId);
        v.setLabel(label);
        v.setSortOrder(order);
        return v;
    }

    /** 纯 Mockito 无 Spring 上下文，MyBatis-Plus 的 lambda 缓存需显式初始化 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), DishCacheBenchmarkTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, Dish.class);
        TableInfoHelper.initTableInfo(assistant, DishAttributeDimension.class);
        // A4 后候选值查询走 dish_attribute_value 的 LambdaQueryWrapper，同样需要 lambda 缓存
        TableInfoHelper.initTableInfo(assistant, DishAttributeValue.class);
    }

    @BeforeEach
    void setUp() {
        dishMapper = mock(DishMapper.class);
        dimensionMapper = mock(DishAttributeDimensionMapper.class);
        when(dimensionMapper.selectList(any())).thenReturn(DIMENSIONS);
        // 候选值真源 = 取值字典（A4）；原 dishMapper.selectAttributesJsonOnSale 桩已随扫描逻辑退役
        valueMapper = mock(DishAttributeValueMapper.class);
        when(valueMapper.selectList(any())).thenReturn(attributeValues());
        attributeAdminService = mock(DishAttributeAdminService.class);
        // 出参翻译不属本次测量口径：原样透传（解析 JSON 即 identity），保证维度数不变
        when(attributeAdminService.translateForRead(any()))
                .thenAnswer(inv -> JsonMapUtil.parseObject(inv.getArgument(0)));
        when(dishMapper.selectById(1L)).thenReturn(onSaleDish(sampleAttributesJson(0)));
        // A6：视图 = 纯数据 —— 「为你推荐」（条件空）+ 一个种类视图；可见性靠 enabled + 匹配数判定
        viewMapper = mock(DishFilterViewMapper.class);
        when(viewMapper.selectList(any())).thenReturn(List.of(
                view(1L, "为你推荐", "[]", "random", 1),
                view(2L, "面食粉类", "[{\"field\":\"mealTypeId\",\"op\":\"=\",\"value\":\"7\"}]", "random", 2)));
        // 匹配数 > 0 ⇒ 可见（匹配数 0 不下发）
        when(dishMapper.selectCount(any())).thenReturn(1L);

        cacheManager = CacheConfig.buildCacheManager();
        DishAttributeCatalog target = new DishAttributeCatalog(dimensionMapper, valueMapper, cacheManager);
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

        // 视图目录同样要过代理：A6 后 DISH_VIEWS 的 @Cacheable 落在 DishViewCatalog#visible 上
        ProxyFactory viewFactory = new ProxyFactory(new DishViewCatalog(viewMapper, dishMapper, cacheManager));
        viewFactory.setProxyTargetClass(true);
        viewFactory.addAdvice(interceptor);
        viewCatalog = (DishViewCatalog) viewFactory.getProxy();

        // service 也要过代理（listDishViews 已无 @Cacheable，缓存上移到目录；此处保留代理以贴合生产装配）
        ProxyFactory serviceFactory = new ProxyFactory(new DishServiceImpl(dishMapper,
                mock(DishViewLogMapper.class), mock(StallService.class),
                mock(ApplicationEventPublisher.class), mock(ImageUrlUtil.class), catalog, attributeAdminService,
                mock(DishCategoryAdminService.class), viewCatalog));
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
                "热路径（第 2~" + CALLS + " 次）；基线=1（单次取行；维度字典与取值字典均命中缓存）；冷路径 " + coldCalls);
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
                "第二次进入首页的 Mapper 增量；基线=1（无缓存时：每次查一遍视图行 + 逐视图计数）");
        // 诊断/护栏：先确认「缓存里真有条目」，再谈增量——若此处为 0，说明 @Cacheable 根本没接上，
        // 后面的 0 增量断言就没有意义（避免把「装配错误」误读成「性能没提升」）。
        assertThat(cachedEntries(CacheConfig.DISH_VIEWS))
                .as("@Cacheable 未写入缓存：检查 CacheInterceptor 装配与代理是否生效").isEqualTo(1L);
        // A6 口径：**缓存的是「计算后的可见列表」**（含逐视图计数），故热路径零查询 ——
        // 若只缓存原始行，这里会是「视图数」次计数查询。
        assertThat(repeatCalls).isZero();
        assertThat(second).isEqualTo(first);
        // 可见性靠 enabled + 匹配数判定，顺序按 order 升序；出参恰 id + label
        assertThat(first).extracting(DishViewVO::getId).containsExactly(1L, 2L);
        assertThat(first).extracting(DishViewVO::getLabel).containsExactly("为你推荐", "面食粉类");

        // 写后显式失效 ⇒ 保存即生效（不必等 TTL）
        viewCatalog.invalidateViews();
        dishService.listDishViews();
        assertThat(countCalls() - afterFirst).isPositive();
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
                "失效后首次请求的 Mapper 调用（取行 + 维度字典 + 取值字典重算；维度字典已单独缓存故此处仅候选路径重查）");
        // 护栏③：失效必须真的生效——否则新写入的取值最长要等一整个 TTL 才出现在候选里。
        // 口径（A4）：候选重算只查「取值字典」一次（按 dimensionId 直归组，不再回查维度字典）
        // ⇒ 取行(1) + 取值(1) = 2。
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

    /**
     * 读路径的 Mapper 调用总数。
     * <p>
     * <b>2026-10-03 口径变更（A4）</b>：候选值真源由「扫 dish.attributes」改为「直读取值字典」，
     * 故 `valueMapper` 必须纳入计数 —— 否则「失效后候选是否真的重算」会因为计数器看不到字典查询
     * 而变成一个**恒真**的空断言（这正是本次改动暴露出来的盲区）。
     */
    private long countCalls() {
        return mockingDetails(dishMapper).getInvocations().size()
                + mockingDetails(dimensionMapper).getInvocations().size()
                + mockingDetails(valueMapper).getInvocations().size()
                + mockingDetails(viewMapper).getInvocations().size();
    }

    /** 视图行样本（A6）：条件与排序口径同表存储（除文案 / 顺序外全在行内） */
    private static DishFilterView view(Long id, String label, String conditions, String sortKind, int order) {
        DishFilterView v = new DishFilterView();
        v.setId(id);
        v.setLabel(label);
        v.setConditions(conditions);
        v.setSortKind(sortKind);
        v.setSortOrder(order);
        v.setEnabled(true);
        return v;
    }

    private static DishAttributeDimension dimension(Long id, String name,
                                                    String valueType, Integer order) {
        DishAttributeDimension dim = new DishAttributeDimension();
        dim.setId(id);
        dim.setName(name);
        dim.setValueType(valueType);
        dim.setSortOrder(order);
        return dim;
    }

    private static Dish onSaleDish(String attributes) {
        Dish dish = new Dish();
        dish.setId(1L);
        dish.setStatus(DishConst.STATUS_ON);
        dish.setAttributes(attributes);
        return dish;
    }

    /** 单菜属性 JSON：4 个维度各一个值（键 = 维度 ID 字符串；多选维度为数组，与生产落库形态一致） */
    private static String sampleAttributesJson(int seed) {
        return "{\"1\":\"v" + (seed % 20) + "\",\"2\":\"v" + ((seed + 3) % 20)
                + "\",\"3\":[\"v" + ((seed + 7) % 20) + "\"],\"4\":\"v"
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

