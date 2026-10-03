package com.bjtufood.dish.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.config.CacheConfig;
import com.bjtufood.dish.dto.DishViewCondition;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.entity.DishFilterView;
import com.bjtufood.dish.mapper.DishFilterViewMapper;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.view.DishViewConditions;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 首页筛选视图的**目录数据**（表 `dish_filter_view` 驱动，A6 落地 2026-10-03）。
 *
 * <p><b>为什么单独成类</b>：与 {@code DishAttributeCatalog} 同理 —— Spring 声明式缓存靠代理实现，
 * <b>自调用不过代理</b>；把「可缓存的目录数据」抽成独立 bean，由 service 注入调用，代理必然生效。
 *
 * <p><b>下发规则</b>（契约见 docs/schema/dish_filter_view.md 的「客户端可见性」）：
 * 只下发 `enabled = 1` 的视图，且**匹配数为 0 的不下发**（避免点进空列表），
 * **默认视图恒下发**（它是空 `view` 的落点）；顺序 = `order` 升序、**默认视图排首位**。
 *
 * <p><b>缓存</b>：`all()` 走 `@Cacheable`（TTL 见 {@link CacheConfig}）；A6 的写入口
 * （视图 / 分类值的增删改、排序、设默认）一律调用 {@link #invalidateViews()} 显式失效，做到**保存即生效**。
 */
@Component
@RequiredArgsConstructor
public class DishViewCatalog {

    private final DishFilterViewMapper viewMapper;
    private final DishMapper dishMapper;
    /** 仅用于<b>写侧显式失效</b>；读侧装配由 {@code @Cacheable} + {@link CacheConfig} 完成 */
    private final CacheManager cacheManager;

    /**
     * 全部视图（按 `order` 升序、`order` 相同按 id 稳定）。
     * <p>
     * <b>刻意不缓存</b>：它是「按键解析」与「管理端列表」的输入，二者都需要**最新**数据
     * （管理端改完立刻要看到自己改的行）；真正昂贵的是 {@link #visible()} 的逐视图计数，
     * 那才是缓存对象。
     */
    public List<DishFilterView> all() {
        return List.copyOf(viewMapper.selectList(new LambdaQueryWrapper<DishFilterView>()
                .orderByAsc(DishFilterView::getOrder)
                .orderByAsc(DishFilterView::getId)));
    }

    /** 默认视图（`is_default = 1`；应用层保证全站恰一个）。约定缺失时返回 {@code null}。 */
    public DishFilterView defaultView() {
        return all().stream().filter(v -> Boolean.TRUE.equals(v.getIsDefault())).findFirst().orElse(null);
    }

    /**
     * 按键解析视图：空 = 默认视图；未登记 = {@code null}（由调用方按白名单非法值抛 `400`，不静默降级）。
     */
    public DishFilterView byKey(String key) {
        if (key == null || key.isBlank()) {
            return defaultView();
        }
        return all().stream().filter(v -> key.equals(v.getKey())).findFirst().orElse(null);
    }

    /**
     * 端上可见视图（`enabled` + 匹配数规则；默认视图排首位）。
     * <p>
     * <b>缓存的是本方法的结果</b>（而非原始行）：判定可见性需要**逐视图一次计数查询**，
     * 只缓存原始行会让「第二次进首页」依旧发 N 次计数 —— 那正是这次缓存要省掉的成本。
     * 写侧 {@link #invalidateViews()} 显式失效 ⇒ **保存即生效**，不需要等 TTL。
     * <p>
     * 返回不可变列表（跨请求共享，调用方改写会污染缓存）。
     *
     * @return 可见视图（已按规则过滤与排序）
     */
    @Cacheable(CacheConfig.DISH_VIEWS)
    public List<DishFilterView> visible() {
        List<DishFilterView> result = new ArrayList<>();
        for (DishFilterView view : all()) {
            boolean isDefault = Boolean.TRUE.equals(view.getIsDefault());
            if (!Boolean.TRUE.equals(view.getEnabled())) {
                continue;
            }
            // 匹配数为 0 不下发（避免用户点进空列表）；默认视图恒下发（空 view 的落点）
            if (!isDefault && matchedCount(view) == 0) {
                continue;
            }
            result.add(view);
        }
        // 默认视图排首位；其余保持 order 升序（all() 已排序）
        result.sort(Comparator.comparing((DishFilterView v) -> !Boolean.TRUE.equals(v.getIsDefault())));
        return List.copyOf(result);
    }

    /**
     * 某视图当前匹配的**在售**菜品数。
     *
     * @param view 视图行（`conditions` JSON 为原文）
     */
    public long matchedCount(DishFilterView view) {
        return matchedCount(DishViewConditions.parse(view.getConditions()));
    }

    /**
     * 给定条件命中的**在售**菜品数（预览端点复用同一口径）。
     */
    public long matchedCount(List<DishViewCondition> conditions) {
        return dishMapper.selectCount(DishViewConditions.toWrapper(conditions, true));
    }

    /** 失效视图目录缓存：A6 的一切写入口（视图 / 分类值）都须调用，做到保存即生效。 */
    public void invalidateViews() {
        Cache cache = cacheManager.getCache(CacheConfig.DISH_VIEWS);
        if (cache == null) {
            // 缓存名是编译期常量、由 CacheConfig 注册——取不到就是装配被破坏；
            // 静默返回等于「保存了但端上要等一整个 TTL 才生效」，而这种 bug 在读侧完全看不出来。
            throw new IllegalStateException(
                    "缓存未装配：CacheManager 中找不到 " + CacheConfig.DISH_VIEWS + "，检查 CacheConfig");
        }
        cache.clear();
    }

    /** 抽样菜名（预览用，供管理员判断条件对不对；只取 id + name，避免拉全行） */
    public List<String> sampleNames(List<DishViewCondition> conditions, int limit) {
        return dishMapper.selectList(DishViewConditions.toWrapper(conditions, true)
                        .select("id", "name")
                        .last("LIMIT " + Math.max(1, limit)))
                .stream()
                .map(Dish::getName)
                .toList();
    }
}
