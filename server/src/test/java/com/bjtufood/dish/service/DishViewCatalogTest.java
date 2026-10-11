package com.bjtufood.dish.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.dish.constant.DishConst;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.entity.DishFilterView;
import com.bjtufood.dish.mapper.DishFilterViewMapper;
import com.bjtufood.dish.mapper.DishMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.CacheManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DishViewCatalog} 的**视图行驱动**边界护栏：匹配数按视图行 `categoryId` 过滤
 * （NULL = 不按种类过滤）且恒带在售过滤；可见列表只含启用且匹配数 &gt; 0 的视图；按 `id` 升序下发。
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

    private static DishFilterView view(Long id, String name, Long categoryId) {
        DishFilterView v = new DishFilterView();
        v.setId(id);
        v.setName(name);
        v.setCategoryId(categoryId);
        v.setEnabled(true);
        return v;
    }

    @Test
    @DisplayName("可见列表只含启用且匹配数 > 0 的视图；byId 命中 / 缺省取首个启用视图 / 未登记返回 null")
    void visibleAndResolvable() {
        DishFilterView recommend = view(1L, "为你推荐", null);
        DishFilterView noodle = view(2L, "面食粉类", 7L);
        DishFilterView off = view(3L, "已停用", null);
        off.setEnabled(false);
        when(viewMapper.selectList(any())).thenReturn(List.of(recommend, noodle, off));
        when(dishMapper.selectCount(any())).thenReturn(2L);

        // 传 id → 命中该行；不传 → 首个启用视图
        assertThat(catalog.byId(2L)).isSameAs(noodle);
        assertThat(catalog.byId(null)).isSameAs(recommend);
        // 未登记的 id → null（调用方按 400 处理，不静默降级）
        assertThat(catalog.byId(99L)).isNull();
        // 已停用行不在可见集合
        assertThat(catalog.visible()).extracting(DishFilterView::getId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("匹配数为 0 的启用视图不下发（避免端上点进空列表）")
    void zeroMatched_notDelivered() {
        DishFilterView recommend = view(1L, "为你推荐", null);
        DishFilterView empty = view(2L, "空视图", 7L);
        when(viewMapper.selectList(any())).thenReturn(List.of(recommend, empty));
        // 逐视图计数：首个视图命中 2，第二个视图命中 0
        when(dishMapper.selectCount(any())).thenReturn(2L, 0L);

        assertThat(catalog.visible()).extracting(DishFilterView::getId).containsExactly(1L);
    }

    @Test
    @DisplayName("matchedCount：恒带在售过滤；categoryId 非空时叠加该种类条件，视图缺失计 0")
    void matchedCount_appliesCategoryFilter() {
        DishFilterView recommend = view(1L, "为你推荐", null);
        DishFilterView noodle = view(2L, "面食粉类", 7L);
        when(dishMapper.selectCount(any(QueryWrapper.class))).thenReturn(3L);

        assertThat(catalog.matchedCount(recommend)).isEqualTo(3L);
        assertThat(catalog.matchedCount(noodle)).isEqualTo(3L);
        assertThat(catalog.matchedCount(null)).isZero();

        // 护栏（结构化判据，不比对 SQL 文本）：
        // ① 参数值集 —— 两次计数都绑定「在售」值，种类视图额外绑定该视图的 categoryId；
        // ② 条件字段集 —— 参与条件的列只允许在售态与种类两列，多绑一列即失败。
        ArgumentCaptor<QueryWrapper<Dish>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(dishMapper, times(2)).selectCount(captor.capture());
        List<QueryWrapper<Dish>> wrappers = captor.getAllValues();
        assertThat(boundValues(wrappers.get(0))).containsExactly(DishConst.STATUS_ON);
        assertThat(boundValues(wrappers.get(1))).containsExactlyInAnyOrder(DishConst.STATUS_ON, 7L);
        assertThat(conditionColumns(wrappers.get(0))).containsExactly("status");
        assertThat(conditionColumns(wrappers.get(1))).containsExactlyInAnyOrder("status", "category_id");
    }

    /**
     * 包装器绑定的参数值集（MyBatis-Plus 的 `MPGENVALn` → 实参值）。
     * <p>
     * 先取一次 SQL 片段：MyBatis-Plus 是在**生成片段时**才把实参登记进 {@code paramNameValuePairs}
     * （惰性占位），不触发则读到空集。
     */
    private static Set<Object> boundValues(QueryWrapper<Dish> wrapper) {
        wrapper.getSqlSegment();
        return new LinkedHashSet<>(wrapper.getParamNameValuePairs().values());
    }

    /**
     * 条件涉及的**列集**：先取 `dish` 表的真实列名，再在 SQL 片段中按标识符匹配——
     * 只有列名命中才算一项条件字段，故「条件绑错列」「多绑一列」都会被断言捕获。
     */
    private static Set<String> conditionColumns(QueryWrapper<Dish> wrapper) {
        Set<String> dishColumns = TableInfoHelper.getTableInfo(Dish.class).getFieldList().stream()
                .map(TableFieldInfo::getColumn)
                .collect(Collectors.toSet());
        Matcher matcher = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*").matcher(wrapper.getSqlSegment());
        Set<String> hit = new LinkedHashSet<>();
        while (matcher.find()) {
            if (dishColumns.contains(matcher.group())) {
                hit.add(matcher.group());
            }
        }
        return hit;
    }
}
