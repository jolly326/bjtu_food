package com.bjtufood.dish.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.config.CacheConfig;
import com.bjtufood.dish.dto.DishViewCondition;
import com.bjtufood.dish.entity.DishFilterView;
import com.bjtufood.dish.mapper.DishFilterViewMapper;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.view.DishViewConditions;
import com.bjtufood.dish.view.DishViewDefs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页筛选视图的**目录数据**（表 `dish_filter_view` 驱动，A6 落地 2026-10-03）。
 *
 * <p><b>为什么单独成类</b>：与 {@code DishAttributeCatalog} 同理 —— Spring 声明式缓存靠代理实现，
 * <b>自调用不过代理</b>；把「可缓存的目录数据」抽成独立 bean，由 service 注入调用，代理必然生效。
 *
 * <p><b>下发规则</b>（契约见 docs/schema/dish_filter_view.md 的「客户端可见性」）：
 * 只下发 `enabled = 1` 的视图，且**匹配数为 0 的不下发**（避免点进空列表），顺序 = `sort_order` 升序。
 * 无「默认视图」概念：空 `view` 取**首个启用视图**。
 *
 * <p><b>逻辑取自代码</b>：视图的筛选条件与排序口径不落库，按 `key` 从 {@link DishViewDefs} 取；
 * 表行的 `key` 在 {@code DishViewDefs} 无定义 ⇒ 逻辑缺失，该视图**不下发**、按键解析返回 `null`。
 *
 * <p><b>缓存</b>：`visible()` 走 `@Cacheable`（TTL 见 {@link CacheConfig}）；A6 的写入口
 * （视图改文案启停、排序）一律调用 {@link #invalidateViews()} 显式失效，做到**保存即生效**。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DishViewCatalog {

    private final DishFilterViewMapper viewMapper;
    private final DishMapper dishMapper;
    /** 仅用于<b>写侧显式失效</b>；读侧装配由 {@code @Cacheable} + {@link CacheConfig} 完成 */
    private final CacheManager cacheManager;

    /**
     * 全部视图（按 `sort_order` 升序、`sort_order` 相同按 id 稳定）。
     * <p>
     * <b>刻意不缓存</b>：它是「按键解析」与「管理端列表」的输入，二者都需要**最新**数据
     * （管理端改完立刻要看到自己改的行）；真正昂贵的是 {@link #visible()} 的逐视图计数，
     * 那才是缓存对象。
     */
    public List<DishFilterView> all() {
        return List.copyOf(viewMapper.selectList(new LambdaQueryWrapper<DishFilterView>()
                .orderByAsc(DishFilterView::getSortOrder)
                .orderByAsc(DishFilterView::getId)));
    }

    /**
     * 按键解析视图：空 = **首个启用视图**（按 `sort_order` 升序）；未登记 / 逻辑无定义 = {@code null}
     * （由调用方按白名单非法值抛 `400`，不静默降级）。
     * <p>
     * 「逻辑无定义」指表行的 `key` 在 {@link DishViewDefs} 中不存在 —— 此时无筛选条件与排序口径可用。
     */
    public DishFilterView byKey(String key) {
        DishFilterView view;
        if (key == null || key.isBlank()) {
            view = all().stream().filter(v -> Boolean.TRUE.equals(v.getEnabled())).findFirst().orElse(null);
        } else {
            view = all().stream().filter(v -> key.equals(v.getKey())).findFirst().orElse(null);
        }
        if (view != null && !DishViewDefs.contains(view.getKey())) {
            log.warn("筛选视图 key 在 DishViewDefs 无定义，按未登记处理：key={}, id={}", view.getKey(), view.getId());
            return null;
        }
        return view;
    }

    /**
     * 端上可见视图（`enabled` + 匹配数规则；`sort_order` 升序）。
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
            if (!Boolean.TRUE.equals(view.getEnabled())) {
                continue;
            }
            DishViewDefs.Def def = DishViewDefs.byKey(view.getKey());
            if (def == null) {
                // 逻辑无定义（表行 key 与 DishViewDefs 不一致）：不下发，避免端上点到无筛选语义的 tab
                log.warn("筛选视图 key 在 DishViewDefs 无定义，不下发：key={}, id={}", view.getKey(), view.getId());
                continue;
            }
            // 匹配数为 0 不下发（避免用户点进空列表）
            if (matchedCount(def.conditions()) == 0) {
                continue;
            }
            result.add(view);
        }
        // all() 已按 order 升序、id 稳定排序
        return List.copyOf(result);
    }

    /**
     * 某视图当前匹配的**在售**菜品数（条件按 `key` 从 {@link DishViewDefs} 取）。
     *
     * @param view 视图行；`key` 在 {@link DishViewDefs} 无定义时计 0（管理端列表照常展示该行）
     */
    public long matchedCount(DishFilterView view) {
        DishViewDefs.Def def = view == null ? null : DishViewDefs.byKey(view.getKey());
        return def == null ? 0L : matchedCount(def.conditions());
    }

    /**
     * 给定条件命中的**在售**菜品数。
     */
    public long matchedCount(List<DishViewCondition> conditions) {
        return dishMapper.selectCount(DishViewConditions.toWrapper(conditions, true));
    }

    /** 失效视图目录缓存：A6 的一切写入口（视图改文案启停 / 排序）都须调用，做到保存即生效。 */
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
}
