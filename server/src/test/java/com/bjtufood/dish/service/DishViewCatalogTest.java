package com.bjtufood.dish.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.entity.DishFilterView;
import com.bjtufood.dish.mapper.DishFilterViewMapper;
import com.bjtufood.dish.mapper.DishMapper;
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
 * {@link DishViewCatalog} 的**逻辑取自代码**边界护栏：表行 `key` 在 {@code DishViewDefs}
 * 无定义时不下发、按键解析返回 {@code null}、匹配数计 0（管理端列表照常展示该行）。
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

    private static DishFilterView view(Long id, String key, String label, int order) {
        DishFilterView v = new DishFilterView();
        v.setId(id);
        v.setKey(key);
        v.setLabel(label);
        v.setOrder(order);
        v.setEnabled(true);
        return v;
    }

    @Test
    @DisplayName("合法 key：可见列表只含启用且匹配数 > 0 的视图，byKey 命中")
    void validKeys_visibleAndResolvable() {
        when(viewMapper.selectList(any())).thenReturn(List.of(
                view(1L, "recommend", "为你推荐", 1),
                view(2L, "noodle", "面食粉类", 2)));
        when(dishMapper.selectCount(any())).thenReturn(2L);

        assertThat(catalog.byKey("noodle")).extracting(DishFilterView::getKey).isEqualTo("noodle");
        assertThat(catalog.byKey(null)).extracting(DishFilterView::getKey).isEqualTo("recommend");
        assertThat(catalog.visible()).extracting(DishFilterView::getKey)
                .containsExactly("recommend", "noodle");
    }

    @Test
    @DisplayName("未知 key：不下发 + byKey 返回 null + matchedCount 计 0")
    void unknownKey_notDelivered() {
        when(viewMapper.selectList(any())).thenReturn(List.of(
                view(1L, "recommend", "为你推荐", 1),
                view(2L, "ghost", "幽灵视图", 2)));
        when(dishMapper.selectCount(any())).thenReturn(1L);

        assertThat(catalog.byKey("ghost")).isNull();
        assertThat(catalog.matchedCount(view(2L, "ghost", "幽灵视图", 2))).isZero();
        assertThat(catalog.visible()).extracting(DishFilterView::getKey).containsExactly("recommend");
    }
}
