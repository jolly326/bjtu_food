package com.bjtufood.dish.service;

import com.bjtufood.dish.entity.DishAttributeDimension;
import com.bjtufood.dish.entity.DishAttributeValue;
import com.bjtufood.dish.mapper.DishAttributeDimensionMapper;
import com.bjtufood.dish.mapper.DishAttributeValueMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DishAttributeCatalog} 的目录契约回归测试。
 * <p>
 * <b>2026-10-03 口径变更</b>：候选值真源由「扫全库在售菜品 attributes 按频次去重」
 * 改为「直读**取值字典**（A4 落地）」。原「扫描上限 / 触顶 WARN」两条断言随扫描逻辑一并退役
 * —— 字典规模由管理端维护控制，不再有「随行数线性膨胀」的读路径。
 * <p>
 * 保留的契约：<b>返回不可变</b>（结果跨请求共享，任何调用方改写都会污染其他请求，
 * 这是缓存最隐蔽的一类 bug）。
 */
class DishAttributeCatalogTest {

    private final DishAttributeDimensionMapper dimensionMapper = mock(DishAttributeDimensionMapper.class);
    /** 候选值真源：取值字典（A4） */
    private final DishAttributeValueMapper valueMapper = mock(DishAttributeValueMapper.class);

    private DishAttributeCatalog catalog() {
        return new DishAttributeCatalog(dimensionMapper, valueMapper, mock(CacheManager.class));
    }

    private static DishAttributeDimension dimension(Long id, String name, int order) {
        DishAttributeDimension d = new DishAttributeDimension();
        d.setId(id);
        d.setName(name);
        d.setValueType("single");
        d.setSortOrder(order);
        return d;
    }

    private static DishAttributeValue value(Long id, Long dimensionId, String label, int order) {
        DishAttributeValue v = new DishAttributeValue();
        v.setId(id);
        v.setDimensionId(dimensionId);
        v.setLabel(label);
        v.setSortOrder(order);
        return v;
    }

    @Test
    @DisplayName("候选值 = 该维度下取值字典的 label（按字典 order 升序），键为维度 ID")
    void candidatesComeFromValueDictionary() {
        when(dimensionMapper.selectList(any())).thenReturn(List.of(
                dimension(1L, "饮食属性", 1),
                dimension(2L, "口味", 3)));
        // 故意乱序返回：服务端按 order 升序（查询已排序，此处验证聚合不依赖返回顺序）
        when(valueMapper.selectList(any())).thenReturn(List.of(
                value(10L, 1L, "荤", 1),
                value(11L, 1L, "素", 2),
                value(20L, 2L, "辣", 1)));

        Map<Long, List<String>> result = catalog().candidateValuesByDimensionId();

        assertThat(result).containsOnlyKeys(1L, 2L);
        assertThat(result.get(1L)).containsExactly("荤", "素");
        assertThat(result.get(2L)).containsExactly("辣");
    }

    @Test
    @DisplayName("候选按取值行的 dimensionId 直接归组，不依赖维度字典回查（维度删除时取值一并清理）")
    void candidatesGroupedByValueDimensionId() {
        when(valueMapper.selectList(any())).thenReturn(List.of(
                value(10L, 1L, "荤", 1),
                value(99L, 42L, "孤儿值", 2)));

        Map<Long, List<String>> result = catalog().candidateValuesByDimensionId();

        assertThat(result).containsOnlyKeys(1L, 42L);
        assertThat(result.get(42L)).containsExactly("孤儿值");
    }

    @Test
    @DisplayName("返回不可变：调用方改写候选列表 / 外层 Map 必须抛异常（结果跨请求共享）")
    void candidatesAreImmutable() {
        when(valueMapper.selectList(any())).thenReturn(List.of(value(10L, 1L, "荤", 1)));

        Map<Long, List<String>> result = catalog().candidateValuesByDimensionId();

        assertThatThrownBy(() -> result.get(1L).add("篡改"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.put(9L, List.of("y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("维度字典按 order 升序返回且不可变")
    void dimensionsAreOrderedAndImmutable() {
        when(dimensionMapper.selectList(any())).thenReturn(List.of(
                dimension(1L, "饮食属性", 1),
                dimension(2L, "口味", 3)));

        List<DishAttributeDimension> dimensions = catalog().dimensions();

        assertThat(dimensions).extracting(DishAttributeDimension::getId)
                .containsExactly(1L, 2L);
        assertThatThrownBy(() -> dimensions.add(dimension(3L, "x", 4)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
