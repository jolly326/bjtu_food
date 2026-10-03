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
 * A6 分类值字典的约束与「自动登记」单测（口径见
 * docs/web/A-主数据维护/A6-首页筛选视图管理.md 与 docs/schema/dish_category_value.md）：
 * <ol>
 *   <li><b>删除受引用约束</b>：被菜品引用 → {@code 400}（清理同义值走合并）；</li>
 *   <li><b>合并</b>：把 from 的菜品改指 to 后删 from；源/目标相同或不存在 → {@code 400}；</li>
 *   <li><b>自动登记</b>：A3 输入新分类 → 落库；已存在 → 原样返回、不重复插入；键非法 → {@code 400}；</li>
 *   <li><b>重命名唯一</b>：同名字 → {@code 400}。</li>
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
    @DisplayName("delete：被菜品引用 → 400（清理同义值请用合并）")
    void delete_referencedByDish_rejected400() {
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, "noodle", "面食粉类", 3));
        when(dishMapper.selectCount(any())).thenReturn(7L);

        assertThatThrownBy(() -> service().delete(3L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        // BaseMapper 的 deleteById 有 (Serializable) 与 (T) 两个重载：显式给 Long 消歧
        verify(categoryMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("merge：源与目标相同 → 400")
    void merge_sameIds_rejected400() {
        assertThatThrownBy(() -> service().merge(3L, 3L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(dishMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("merge：源或目标不存在 → 400")
    void merge_missingEndpoint_rejected400() {
        when(categoryMapper.selectById(7L)).thenReturn(null);
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, "noodle", "面食粉类", 3));

        assertThatThrownBy(() -> service().merge(7L, 3L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    @DisplayName("merge：正常 —— 批量改指 dish.meal_type 后删源行")
    void merge_rewritesDishesThenDeletesSource() {
        when(categoryMapper.selectById(7L)).thenReturn(category(7L, "noodle-old", "面食", 9));
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, "noodle", "面食粉类", 3));

        service().merge(7L, 3L);

        // ① 参数化批量 UPDATE（不逐行读改）
        verify(dishMapper).update(any(), any());
        // ② 目标保留、源行删除
        verify(categoryMapper).deleteById(7L);
        verify(categoryMapper, never()).deleteById(3L);
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
