package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.entity.DishCategoryValue;
import com.bjtufood.dish.mapper.DishCategoryValueMapper;
import com.bjtufood.dish.mapper.DishMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A6 分类值字典的契约与「自动登记」单测（口径见
 * docs/api/web/categories.md 与 docs/schema/dish_category_value.md）：
 * <ol>
 *   <li><b>自动登记</b>：A3 输入新分类 → 落库；已存在 → 原样返回、不重复插入；键非法 → {@code 400}；</li>
 *   <li><b>登记唯一</b>：键重名 → {@code 400}；</li>
 *   <li><b>列表</b>：按 order 升序返回并带 dishCount。</li>
 * </ol>
 */
class DishCategoryAdminServiceImplTest {

    /** MyBatis-Plus 的 lambda 缓存需显式初始化，否则构造 LambdaQueryWrapper 会抛异常 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(),
                DishCategoryAdminServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, DishCategoryValue.class);
        TableInfoHelper.initTableInfo(assistant, Dish.class);
    }

    private final DishCategoryValueMapper categoryMapper = mock(DishCategoryValueMapper.class);
    private final DishMapper dishMapper = mock(DishMapper.class);

    private DishCategoryAdminServiceImpl service() {
        return new DishCategoryAdminServiceImpl(categoryMapper, dishMapper);
    }

    private static DishCategoryValue category(Long id, String key, String label, int order) {
        DishCategoryValue c = new DishCategoryValue();
        c.setId(id);
        c.setKey(key);
        c.setLabel(label);
        c.setOrder(order);
        return c;
    }

    @Test
    @DisplayName("resolveOrRegister：已存在 → 原样返回且不重复插入")
    void resolveOrRegister_existing_returnsWithoutInsert() {
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertThat(service().resolveOrRegister("noodle")).isEqualTo("noodle");
        verify(categoryMapper, never()).insert(any());
    }

    @Test
    @DisplayName("resolveOrRegister：不存在 → 以键为初名自动登记")
    void resolveOrRegister_missing_registers() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);

        assertThat(service().resolveOrRegister("new-kind")).isEqualTo("new-kind");
        verify(categoryMapper).insert(any());
    }

    @Test
    @DisplayName("resolveOrRegister：键非法（大写 / 超长 / 空）→ 400")
    void resolveOrRegister_illegalKey_rejected400() {
        assertThatThrownBy(() -> service().resolveOrRegister("Noodle"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service().resolveOrRegister(""))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service().resolveOrRegister("a".repeat(21)))
                .isInstanceOf(BusinessException.class);
        verify(categoryMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create：键重名 → 400")
    void create_duplicateKey_rejected400() {
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().create("noodle", "面食粉类"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(categoryMapper, never()).insert(any());
    }

    @Test
    @DisplayName("listAll：按 order 升序返回并带 dishCount")
    void listAll_carriesDishCount() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(3L, "noodle", "面食粉类", 3),
                category(1L, "set_meal", "套餐盖饭", 1)));
        Dish d1 = new Dish();
        d1.setId(1L);
        d1.setMealType("noodle");
        Dish d2 = new Dish();
        d2.setId(2L);
        d2.setMealType("noodle");
        when(dishMapper.selectList(any())).thenReturn(List.of(d1, d2));

        var list = service().listAll();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getKey()).isEqualTo("noodle");
        assertThat(list.get(0).getDishCount()).isEqualTo(2L);
        assertThat(list.get(1).getDishCount()).isZero();
    }
}
