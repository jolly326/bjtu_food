package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.common.dto.SortItem;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A6 菜品种类字典（表 `dish_category`）的契约单测（口径见
 * docs/api/web/categories.md 与 docs/schema/dish_category.md）：
 * <ol>
 *   <li><b>真表 CRUD</b>：一切读写落在 `dish_category`，不存在第二份字典；</li>
 *   <li><b>出参字段</b>：id / label / order / dishCount / updatedAt（无机器键）；</li>
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

    private static DishCategory category(Long id, String label, Integer order) {
        DishCategory c = new DishCategory();
        c.setId(id);
        c.setLabel(label);
        c.setSortOrder(order);
        return c;
    }

    private static Dish dish(long id, Long categoryId) {
        Dish d = new Dish();
        d.setId(id);
        d.setCategoryId(categoryId);
        return d;
    }

    private static SortItem sortItem(Long id, Integer order) {
        SortItem item = new SortItem();
        item.setId(id);
        item.setOrder(order);
        return item;
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
    @DisplayName("listAll：按 order 升序出参，dishCount = 引用该种类的菜品数（未被引用为 0）")
    void listAll_carriesDishCount() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(1L, "套餐盖饭", 1), category(2L, "面食粉类", 2)));
        when(dishMapper.selectList(any())).thenReturn(List.of(
                dish(1L, 2L),
                dish(2L, 2L),
                // 未归入分类的菜品不计入任何种类
                dish(3L, null)));

        List<DishCategoryAdminVO> list = service().listAll();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getId()).isEqualTo(1L);
        assertThat(list.get(0).getLabel()).isEqualTo("套餐盖饭");
        assertThat(list.get(0).getOrder()).isEqualTo(1);
        assertThat(list.get(0).getDishCount()).isZero();
        assertThat(list.get(1).getLabel()).isEqualTo("面食粉类");
        assertThat(list.get(1).getDishCount()).isEqualTo(2L);
    }

    // ==================== create ====================

    @Test
    @DisplayName("create：名称去空白、默认排最后（当前最大 order + 1），出参 dishCount = 0")
    void create_savesWithNextOrder() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(1L, "套餐盖饭", 4)));
        AtomicReference<DishCategory> inserted = new AtomicReference<>();
        when(categoryMapper.insert(any())).thenAnswer(invocation -> {
            DishCategory entity = invocation.getArgument(0);
            entity.setId(9L);
            inserted.set(entity);
            return 1;
        });
        when(categoryMapper.selectById(9L)).thenAnswer(invocation -> inserted.get());

        DishCategoryAdminVO vo = service().create("  面食粉类  ");

        assertThat(inserted.get().getLabel()).isEqualTo("面食粉类");
        assertThat(inserted.get().getSortOrder()).isEqualTo(5);
        assertThat(vo.getId()).isEqualTo(9L);
        assertThat(vo.getLabel()).isEqualTo("面食粉类");
        assertThat(vo.getOrder()).isEqualTo(5);
        assertThat(vo.getDishCount()).isZero();
    }

    @Test
    @DisplayName("create：无任何种类时默认 order = 1")
    void create_firstRowOrderIsOne() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        AtomicReference<DishCategory> inserted = new AtomicReference<>();
        when(categoryMapper.insert(any())).thenAnswer(invocation -> {
            DishCategory entity = invocation.getArgument(0);
            entity.setId(1L);
            inserted.set(entity);
            return 1;
        });
        when(categoryMapper.selectById(1L)).thenAnswer(invocation -> inserted.get());

        service().create("套餐盖饭");

        assertThat(inserted.get().getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("create：名空白 / 超 32 字 → 400，且不落库")
    void create_illegalLabel_rejected400() {
        assertRejected(() -> service().create("   "), 400, "分类名不能为空");
        assertRejected(() -> service().create(null), 400, "分类名不能为空");
        assertRejected(() -> service().create("名".repeat(33)), 400, "分类名不能超过 32 字");

        verify(categoryMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create：label 与既有种类重名 → 400「该分类已存在」，且不落库")
    void create_duplicateLabel_rejected400() {
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
    @DisplayName("rename：只写 label（order 不变，菜品数据零迁移）")
    void rename_onlyLabel() {
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, "面食粉类", 4));
        when(categoryMapper.updateById(any())).thenReturn(1);

        service().rename(3L, "  面食粉面类  ");

        ArgumentCaptor<DishCategory> captor = ArgumentCaptor.forClass(DishCategory.class);
        verify(categoryMapper).updateById(captor.capture());
        DishCategory patch = captor.getValue();
        assertThat(patch.getId()).isEqualTo(3L);
        assertThat(patch.getLabel()).isEqualTo("面食粉面类");
        assertThat(patch.getSortOrder()).isNull();
    }

    @Test
    @DisplayName("rename：名空白 / 超 32 字 / 与他行重名 → 400，且不写库")
    void rename_illegalOrDuplicateLabel_rejected400() {
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, "面食粉类", 4));

        assertRejected(() -> service().rename(3L, " "), 400, "分类名不能为空");
        assertRejected(() -> service().rename(3L, "名".repeat(33)), 400, "分类名不能超过 32 字");

        when(categoryMapper.selectCount(any())).thenReturn(1L);
        assertRejected(() -> service().rename(3L, "家常小炒"), 400, "该分类已存在");

        verify(categoryMapper, never()).updateById(any());
    }

    // ==================== sort ====================

    @Test
    @DisplayName("sort：全量行合法提交 → 逐行写 order（只带 id + order）")
    void sort_valid_updatesEachRow() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(1L, "套餐盖饭", 1), category(2L, "面食粉类", 2)));
        when(categoryMapper.updateById(any())).thenReturn(1);

        service().sort(List.of(sortItem(2L, 1), sortItem(1L, 2)));

        ArgumentCaptor<DishCategory> captor = ArgumentCaptor.forClass(DishCategory.class);
        verify(categoryMapper, times(2)).updateById(captor.capture());
        Map<Long, Integer> orders = new HashMap<>();
        for (DishCategory patch : captor.getAllValues()) {
            orders.put(patch.getId(), patch.getSortOrder());
            assertThat(patch.getLabel()).isNull();
        }
        assertThat(orders).containsOnly(entry(2L, 1), entry(1L, 2));
    }

    @Test
    @DisplayName("sort：空提交 / 缺行 / 未知 id / order 重复 → 400「排序提交非法」，且不写库")
    void sort_illegalSubmission_rejected400() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(1L, "套餐盖饭", 1), category(2L, "面食粉类", 2)));

        assertRejected(() -> service().sort(List.of()), 400, "排序提交非法");
        // 缺行：只提交 1 行，实际有 2 行
        assertRejected(() -> service().sort(List.of(sortItem(1L, 1))), 400, "排序提交非法");
        // 未知 id
        assertRejected(() -> service().sort(List.of(sortItem(1L, 1), sortItem(9L, 2))),
                400, "排序提交非法");
        // order 重复
        assertRejected(() -> service().sort(List.of(sortItem(1L, 1), sortItem(2L, 1))),
                400, "排序提交非法");

        verify(categoryMapper, never()).updateById(any());
    }

    // ==================== requireExists ====================

    @Test
    @DisplayName("requireExists：种类存在 → 通过（不做任何写入）")
    void requireExists_existing_passes() {
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, "面食粉类", 3));

        service().requireExists(3L);

        verify(categoryMapper, never()).insert(any());
        verify(categoryMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("requireExists：种类不存在（含 null）→ 400「分类不存在」（A3 菜品保存的 categoryId 校验）")
    void requireExists_missing_rejected400() {
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, "面食粉类", 3));

        assertRejected(() -> service().requireExists(404L), 400, "分类不存在：404");
        assertThatThrownBy(() -> service().requireExists(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }
}
