package com.bjtufood.dish.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.entity.DishFilterView;
import com.bjtufood.dish.mapper.DishFilterViewMapper;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.view.DishListQuery;
import com.bjtufood.dish.view.DishViewResolver;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DishViewCatalog} 的**视图行驱动**边界护栏：条件与排序口径都取自表行；
 * 条件 JSON 非法（只能由越过接口的直写造成）时该视图不下发、按键解析返回 {@code null}、匹配数计 0。
 */
class DishViewCatalogTest {

    private DishFilterViewMapper viewMapper;
    private DishMapper dishMapper;
    private DishViewCatalog catalog;

    /** 纯 Mockito 无 Spring 上下文，MyBatis-Plus 的 lambda 缓存需显式初始化 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), DishViewCatalogTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, DishFilterView.class);
        TableInfoHelper.initTableInfo(assistant, Dish.class);
    }

    @BeforeEach
    void setUp() {
        viewMapper = mock(DishFilterViewMapper.class);
        dishMapper = mock(DishMapper.class);
        catalog = new DishViewCatalog(viewMapper, dishMapper, mock(CacheManager.class));
    }

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

    @Test
    @DisplayName("合法条件：可见列表只含启用且匹配数 > 0 的视图，byId 命中")
    void validRows_visibleAndResolvable() {
        DishFilterView recommend = view(1L, "为你推荐", "[]", "random", 1);
        DishFilterView noodle = view(2L, "面食粉类",
                "[{\"field\":\"mealTypeId\",\"op\":\"=\",\"value\":\"7\"}]", "random", 2);
        when(viewMapper.selectList(any())).thenReturn(List.of(recommend, noodle));
        when(dishMapper.selectCount(any())).thenReturn(2L);

        // 传 id → 命中该行；不传 → 首个启用视图
        assertThat(catalog.byId(2L)).isSameAs(noodle);
        assertThat(catalog.byId(null)).isSameAs(recommend);
        // 未登记的 id → null（调用方按 400 处理，不静默降级）
        assertThat(catalog.byId(99L)).isNull();
        assertThat(catalog.visible()).extracting(DishFilterView::getId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("条件非法：不下发 + matchedCount 计 0（管理端列表照常展示该行）")
    void illegalConditions_notDelivered() {
        DishFilterView recommend = view(1L, "为你推荐", "[]", "random", 1);
        DishFilterView broken = view(2L, "幽灵视图", "{\"field\":\"1=1 --\"}", "random", 2);
        when(viewMapper.selectList(any())).thenReturn(List.of(recommend, broken));
        when(dishMapper.selectCount(any())).thenReturn(1L);

        assertThat(catalog.matchedCount(broken)).isZero();
        assertThat(catalog.visible()).extracting(DishFilterView::getId).containsExactly(1L);
    }

    @Test
    @DisplayName("解析：条件与排序口径都取自视图行（同一行可解析出取数参数）")
    void resolve_usesRowConditions() {
        DishFilterView view = view(2L, "面食粉类",
                "[{\"field\":\"mealTypeId\",\"op\":\"=\",\"value\":\"7\"}]", "random", 2);

        DishListQuery query = DishViewResolver.resolve(view, null, "seed-1");

        assertThat(query).isNotNull();
        assertThat(query.sortKind()).isEqualTo(DishListQuery.SortKind.SEED_RANDOM);
        assertThat(query.seed()).isEqualTo("seed-1");
        assertThat(query.conditions()).singleElement()
                .satisfies(c -> assertThat(c.getField()).isEqualTo("mealTypeId"))
                .satisfies(c -> assertThat(c.getValue()).isEqualTo("7"));
    }

    @Test
    @DisplayName("边界：视图缺失 / 条件非法 / 排序口径非白名单 → resolve 返回 null（Service 抛 400）")
    void unresolvable_resolveNull() {
        assertThat(DishViewResolver.resolve(null, null, null)).isNull();
        assertThat(DishViewResolver.resolve(view(2L, "坏条件", "{", "random", 2), null, null)).isNull();
        assertThat(DishViewResolver.resolve(view(3L, "坏排序", "[]", "heat", 3), null, null)).isNull();
    }
}
