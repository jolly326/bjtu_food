package com.bjtufood.dish.service;

import com.bjtufood.dish.mapper.DishAttributeDimensionMapper;
import com.bjtufood.dish.mapper.DishMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DishAttributeCatalog} 的聚合契约与<b>扫描上限</b>回归测试。
 * <p>
 * 本类只测「可以直接断言的部分」，不重复 {@code DishCacheBenchmarkTest} 的缓存装配验证：
 * <ol>
 *   <li><b>聚合口径</b>：按维度汇总已用值、按使用频次倒序（频次是「编辑弹层默认推荐顺序」的依据）；</li>
 *   <li><b>扫描上限真的被传下去</b>：该查询是读路径上唯一「行数决定返回体积」的查询，
 *       上限是它的内存边界——若某次重构把参数漏掉，接口会重新变成无界查询，而功能测试完全看不出来
 *       （数据量小时结果一样），故必须显式锁定；</li>
 *   <li><b>触顶不报错</b>：候选仅为参考、不构成写入约束 ⇒ 截断应降级返回而非 500；</li>
 *   <li><b>返回不可变</b>：结果跨请求共享，任何调用方改写都会污染其他请求（缓存最隐蔽的一类 bug）。</li>
 * </ol>
 */
class DishAttributeCatalogTest {

    private final DishMapper dishMapper = mock(DishMapper.class);
    private final DishAttributeDimensionMapper dimensionMapper = mock(DishAttributeDimensionMapper.class);

    private DishAttributeCatalog catalog() {
        return new DishAttributeCatalog(dishMapper, dimensionMapper, mock(CacheManager.class));
    }

    @Test
    @DisplayName("聚合：按维度汇总已用中文值，并按使用频次倒序（频次相同按键名稳定）")
    void aggregatesValuesByFrequency() {
        when(dishMapper.selectAttributesJsonOnSale(anyInt())).thenReturn(List.of(
                "{\"dietType\":\"清淡\",\"flavorTags\":[\"辣\",\"酸\"]}",
                "{\"dietType\":\"清淡\",\"flavorTags\":\"酸\"}"));

        Map<String, List<String>> result = catalog().candidateValuesByFieldKey();

        assertThat(result.get("dietType")).containsExactly("清淡");
        // 酸 出现 2 次、辣 1 次 ⇒ 按频次倒序
        assertThat(result.get("flavorTags")).containsExactly("酸", "辣");
        // 上限必须真的传给 Mapper：漏传即退回无界查询，而结果在小数据量下完全一致（只有上限断言能抓住）
        verify(dishMapper).selectAttributesJsonOnSale(DishAttributeCatalog.MAX_SCAN_ROWS);
    }

    @Test
    @DisplayName("触顶：仍正常返回聚合结果（截断降级，不抛异常）——候选仅为参考、不约束写入")
    void truncationDegradesGracefully() {
        List<String> rows = new ArrayList<>(DishAttributeCatalog.MAX_SCAN_ROWS);
        for (int i = 0; i < DishAttributeCatalog.MAX_SCAN_ROWS; i++) {
            rows.add("{\"dietType\":\"清淡\"}");
        }
        when(dishMapper.selectAttributesJsonOnSale(anyInt())).thenReturn(rows);

        Map<String, List<String>> result = catalog().candidateValuesByFieldKey();

        assertThat(result.get("dietType")).containsExactly("清淡");
    }

    @Test
    @DisplayName("返回不可变：调用方改写不得污染共享结果（缓存跨请求共享）")
    void resultIsImmutable() {
        when(dishMapper.selectAttributesJsonOnSale(anyInt())).thenReturn(List.of("{\"dietType\":\"清淡\"}"));

        Map<String, List<String>> result = catalog().candidateValuesByFieldKey();

        assertThatThrownBy(() -> result.put("spiceLevel", List.of("辣")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.get("dietType").add("辣"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
