package com.bjtufood.dish.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.config.CacheConfig;
import com.bjtufood.common.utils.JsonMapUtil;
import com.bjtufood.dish.entity.DishAttributeDimension;
import com.bjtufood.dish.entity.DishAttributeValue;
import com.bjtufood.dish.mapper.DishAttributeDimensionMapper;
import com.bjtufood.dish.mapper.DishAttributeValueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜品描述属性的<b>目录数据</b>：维度字典 + 编辑候选值。
 * <p>
 * <b>为什么单独成类</b>（而不是留在 {@code DishServiceImpl} 里加注解）：Spring 的声明式缓存靠代理实现，
 * <b>自调用不过代理</b>——留在原类里 {@code this.candidateValuesByFieldKey()} 上的 {@code @Cacheable}
 * 会静默失效（编译期与启动期都不报错，只有性能数字会诚实地告诉你它没生效）。
 * 因此把「可缓存的目录数据」抽成独立 bean，由 service 注入调用，代理必然生效。
 * <p>
 * <b>返回值是跨请求共享的</b>：故一律返回不可变视图，杜绝某个请求改动缓存内容污染其它请求
 * （这是缓存最隐蔽的一类 bug：单次请求全对，并发下偶发数据错乱）。
 * <p>
 * 缓存名与 TTL 统一取自 {@link CacheConfig}。
 */
@Component
@RequiredArgsConstructor
public class DishAttributeCatalog {

    // 字段下线（2026-10-03，A4 落地）：dishMapper 与 MAX_SCAN_ROWS 随「扫全库 attributes 聚合候选」
    // 一并删除 —— 候选值改由取值字典直供后，本类不再有任何「随菜品行数线性增长」的读路径
    // （原扫描上限与其 WARN 降级策略随之退役，读路径的内存边界问题自然消失）。

    private final DishAttributeDimensionMapper dimensionMapper;
    /** 取值字典（A4）：候选值真源 */
    private final DishAttributeValueMapper valueMapper;
    /** 仅用于<b>写侧显式失效</b>；读侧的缓存装配由 {@code @Cacheable} + {@link CacheConfig} 完成 */
    private final CacheManager cacheManager;

    /**
     * 维度字典（按 {@code order} 升序）。
     * <p>
     * 应用内没有该表的写入口（由建表种子维护），所以 TTL 可以放宽到字典级（10 分钟）。
     */
    @Cacheable(CacheConfig.ATTRIBUTE_DIMENSIONS)
    public List<DishAttributeDimension> dimensions() {
        return List.copyOf(dimensionMapper.selectList(
                new LambdaQueryWrapper<DishAttributeDimension>()
                        .orderByAsc(DishAttributeDimension::getOrder)));
    }

    /**
     * 编辑候选值（**取值字典驱动**，A4 落地 2026-10-03）：按维度 {@code fieldKey} 汇总
     * 该维度下**取值字典**的 `label`，按字典 `order` 升序。
     * <p>
     * 原实现是「扫全库在售菜品 attributes、按已用值频次去重」——那套依赖「值即中文」的旧模型
     * （A4 后 `attributes` 存的是取值 ID，聚合会得出 ID 串，语义已失效），故改为直读字典。
     * <p>
     * 候选仅为**参考建议、不构成写入约束**（`DishAttributeEditVO#options`）；字典变更时由
     * {@link #invalidateCandidates()} 显式失效（管理端 A4 写入口调用）。
     */
    @Cacheable(CacheConfig.ATTRIBUTE_CANDIDATES)
    public Map<String, List<String>> candidateValuesByFieldKey() {
        Map<Long, String> fieldKeyById = dimensions().stream()
                .collect(Collectors.toMap(DishAttributeDimension::getId, DishAttributeDimension::getFieldKey));
        Map<String, List<String>> result = new HashMap<>();
        for (DishAttributeValue value : valueMapper.selectList(new LambdaQueryWrapper<DishAttributeValue>()
                .orderByAsc(DishAttributeValue::getOrder))) {
            String fieldKey = fieldKeyById.get(value.getDimensionId());
            if (fieldKey != null && StringUtils.hasText(value.getLabel())) {
                result.computeIfAbsent(fieldKey, k -> new ArrayList<>()).add(value.getLabel());
            }
        }
        Map<String, List<String>> immutable = new HashMap<>(result.size());
        result.forEach((key, labels) -> immutable.put(key, Collections.unmodifiableList(labels)));
        return Collections.unmodifiableMap(immutable);
    }

    /**
     * 失效**维度字典**缓存：A4 维度增删改 / 排序后调用（原实现把维度当「只由建表种子维护」，
     * A4 落地后维度已可由管理端维护）。
     */
    public void invalidateDimensions() {
        Cache cache = cacheManager.getCache(CacheConfig.ATTRIBUTE_DIMENSIONS);
        if (cache == null) {
            throw new IllegalStateException(
                    "缓存未装配：CacheManager 中找不到 " + CacheConfig.ATTRIBUTE_DIMENSIONS + "，检查 CacheConfig");
        }
        cache.clear();
    }

    /**
     * 失效候选值缓存：菜品 {@code attributes} 发生写入后调用（纠错采纳、管理端编辑）。
     * <p>
     * 写侧用<b>显式失效</b>而不是 {@code @CacheEvict}：注解式失效在写事务提交前就执行，
     * 一次并发读能把旧聚合结果重新回填并再陈旧一整个 TTL；显式调用点至少让「谁会让候选值变旧」
     * 在代码里可直接读出来（并允许基准测试断言「失效确实发生」）。
     */
    public void invalidateCandidates() {
        Cache cache = cacheManager.getCache(CacheConfig.ATTRIBUTE_CANDIDATES);
        if (cache == null) {
            // 缓存名是编译期常量、由 CacheConfig 注册——取不到就是装配被破坏。
            // 这里必须炸出来：静默返回等于「写完了但候选值没失效」，最坏要等一整个 TTL 才自愈，
            // 而这种 bug 在读侧完全看不出来（候选值只是少了新取值，不报错）。
            throw new IllegalStateException(
                    "缓存未装配：CacheManager 中找不到 " + CacheConfig.ATTRIBUTE_CANDIDATES + "，检查 CacheConfig");
        }
        cache.clear();
    }
}
