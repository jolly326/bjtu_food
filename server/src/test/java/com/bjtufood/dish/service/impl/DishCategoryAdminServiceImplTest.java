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
 * A6 分类值字典的契约单测（口径见
 * docs/api/web/categories.md 与 docs/schema/dish_category_value.md）：
 * <ol>
 *   <li><b>登记</b>：`key` 选填（缺省自动生成）、填了则校验格式与唯一性；`label` 必填且唯一；</li>
 *   <li><b>存在性校验</b>：`requireExists` 供 A3 菜品保存校验 `mealTypeId`；</li>
 *   <li><b>列表</b>：按 order 升序返回并带 dishCount（按分类 ID 统计）。</li>
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
        c.setSortOrder(order);
        return c;
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
    @DisplayName("create：不填 key → 自动生成唯一键（cat- 前缀）后落库")
    void create_blankKey_autoGenerates() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.selectById(any())).thenReturn(category(9L, "cat-0a1b2c3d", "新品类", 9));

        var vo = service().create("  ", "新品类");

        verify(categoryMapper).insert(any());
        assertThat(vo.getKey()).startsWith("cat-");
        assertThat(vo.getKey().length()).isEqualTo("cat-".length() + 8);
    }

    @Test
    @DisplayName("requireExists：分类不存在 → 400（A3 菜品保存的 mealTypeId 白名单校验）")
    void requireExists_missing_rejected400() {
        when(categoryMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service().requireExists(404L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    @DisplayName("listAll：按 order 升序返回并带 dishCount（按分类 ID 统计）")
    void listAll_carriesDishCount() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(3L, "noodle", "面食粉类", 3),
                category(1L, "set_meal", "套餐盖饭", 1)));
        Dish d1 = new Dish();
        d1.setId(1L);
        d1.setMealType(3L);
        Dish d2 = new Dish();
        d2.setId(2L);
        d2.setMealType(3L);
        when(dishMapper.selectList(any())).thenReturn(List.of(d1, d2));

        var list = service().listAll();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getKey()).isEqualTo("noodle");
        assertThat(list.get(0).getDishCount()).isEqualTo(2L);
        assertThat(list.get(1).getDishCount()).isZero();
    }
}
