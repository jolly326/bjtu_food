package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.dish.dto.DishCategoryAdminVO;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.entity.DishCategory;
import com.bjtufood.dish.mapper.DishCategoryMapper;
import com.bjtufood.dish.mapper.DishMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A6 菜品种类字典（表 `dish_category`）的契约单测（口径见
 * docs/api/web/categories.md 与 docs/schema/dish_category.md）：
 * <ol>
 *   <li><b>真表 CRUD</b>：一切读写落在 `dish_category`，不存在第二份字典；</li>
 *   <li><b>出参字段</b>：id / name / dishCount / updatedAt（无机器键）；</li>
 *   <li><b>引用计数</b>：`dishCount` = 引用该种类的菜品数（`dish.category_id` 聚合）；</li>
 *   <li><b>存在性校验</b>：`requireExists` 只认 `dish_category` 内的行。</li>
 * </ol>
 */
class DishCategoryAdminServiceImplTest {

    /** MyBatis-Plus 的 lambda 缓存需显式初始化，否则构造 LambdaQueryWrapper 会抛异常 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(),
                DishCategoryAdminServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, DishCategory.class);
        TableInfoHelper.initTableInfo(assistant, Dish.class);
    }

    private final DishCategoryMapper categoryMapper = mock(DishCategoryMapper.class);
    private final DishMapper dishMapper = mock(DishMapper.class);

    private DishCategoryAdminServiceImpl service() {
        return new DishCategoryAdminServiceImpl(categoryMapper, dishMapper);
    }

    // ==================== 夹具 ====================

    private static DishCategory category(Long id, String name) {
        DishCategory c = new DishCategory();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private static Dish dish(long id, Long categoryId) {
        Dish d = new Dish();
        d.setId(id);
        d.setCategoryId(categoryId);
        return d;
    }

    /** 断言「业务码 + 文案」双锁（避免每个用例重复强制转换样板）。 */
    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
                                       int code, String message) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .hasMessage(message)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(code));
    }

    // ==================== listAll ====================

    @Test
    @DisplayName("listAll：dishCount = 引用该种类的菜品数（未被引用为 0）")
    void listAll_carriesDishCount() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(1L, "套餐盖饭"), category(2L, "面食粉类")));
        when(dishMapper.selectList(any())).thenReturn(List.of(
                dish(1L, 2L),
                dish(2L, 2L),
                // 未归入分类的菜品不计入任何种类
                dish(3L, null)));

        List<DishCategoryAdminVO> list = service().listAll();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getId()).isEqualTo(1L);
        assertThat(list.get(0).getName()).isEqualTo("套餐盖饭");
        assertThat(list.get(0).getDishCount()).isZero();
        assertThat(list.get(1).getName()).isEqualTo("面食粉类");
        assertThat(list.get(1).getDishCount()).isEqualTo(2L);
    }

    // ==================== create ====================

    @Test
    @DisplayName("create：名称去空白落库，出参 dishCount = 0")
    void create_savesTrimmedName() {
        AtomicReference<DishCategory> inserted = new AtomicReference<>();
        when(categoryMapper.insert(any())).thenAnswer(invocation -> {
            DishCategory entity = invocation.getArgument(0);
            entity.setId(9L);
            inserted.set(entity);
            return 1;
        });
        when(categoryMapper.selectById(9L)).thenAnswer(invocation -> inserted.get());

        DishCategoryAdminVO vo = service().create("  面食粉类  ");

        assertThat(inserted.get().getName()).isEqualTo("面食粉类");
        assertThat(vo.getId()).isEqualTo(9L);
        assertThat(vo.getName()).isEqualTo("面食粉类");
        assertThat(vo.getDishCount()).isZero();
    }

    @Test
    @DisplayName("create：名空白 / 超 32 字 → 400，且不落库")
    void create_illegalName_rejected400() {
        assertRejected(() -> service().create("   "), 400, "分类名不能为空");
        assertRejected(() -> service().create(null), 400, "分类名不能为空");
        assertRejected(() -> service().create("名".repeat(33)), 400, "分类名不能超过 32 字");

        verify(categoryMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create：name 与既有种类重名 → 400「该分类已存在」，且不落库")
    void create_duplicateName_rejected400() {
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertRejected(() -> service().create("面食粉类"), 400, "该分类已存在");

        verify(categoryMapper, never()).insert(any());
    }

    // ==================== rename ====================

    @Test
    @DisplayName("rename：种类不存在 → 4001「分类不存在」，且不写库")
    void rename_missing_rejected4001() {
        assertRejected(() -> service().rename(404L, "面食粉类"), 4001, "分类不存在");
        assertRejected(() -> service().rename(null, "面食粉类"), 4001, "分类不存在");

        verify(categoryMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("rename：只写 name（菜品数据零迁移）")
    void rename_onlyName() {
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, "面食粉类"));
        when(categoryMapper.updateById(any())).thenReturn(1);

        service().rename(3L, "  面食粉面类  ");

        ArgumentCaptor<DishCategory> captor = ArgumentCaptor.forClass(DishCategory.class);
        verify(categoryMapper).updateById(captor.capture());
        DishCategory patch = captor.getValue();
        assertThat(patch.getId()).isEqualTo(3L);
        assertThat(patch.getName()).isEqualTo("面食粉面类");
    }

    @Test
    @DisplayName("rename：名空白 / 超 32 字 / 与他行重名 → 400，且不写库")
    void rename_illegalOrDuplicateName_rejected400() {
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, "面食粉类"));

        assertRejected(() -> service().rename(3L, " "), 400, "分类名不能为空");
        assertRejected(() -> service().rename(3L, "名".repeat(33)), 400, "分类名不能超过 32 字");

        when(categoryMapper.selectCount(any())).thenReturn(1L);
        assertRejected(() -> service().rename(3L, "家常小炒"), 400, "该分类已存在");

        verify(categoryMapper, never()).updateById(any());
    }

    // ==================== requireExists ====================

    @Test
    @DisplayName("requireExists：种类存在 → 通过（不做任何写入）")
    void requireExists_existing_passes() {
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, "面食粉类"));

        service().requireExists(3L);

        verify(categoryMapper, never()).insert(any());
        verify(categoryMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("requireExists：种类不存在（含 null）→ 400「分类不存在」（A3 菜品保存 / A6 视图保存的 categoryId 校验）")
    void requireExists_missing_rejected400() {
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, "面食粉类"));

        assertRejected(() -> service().requireExists(404L), 400, "分类不存在：404");
        assertThatThrownBy(() -> service().requireExists(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }
}
