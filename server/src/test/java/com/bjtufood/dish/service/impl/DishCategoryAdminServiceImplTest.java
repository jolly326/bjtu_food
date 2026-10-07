package com.bjtufood.dish.service.impl;

import com.bjtufood.common.dto.SortItem;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.dish.dto.DishValueAdminVO;
import com.bjtufood.dish.service.DishAttributeAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A6 菜品种类字典（**系统维度取值的别名面**）的契约单测（口径见
 * docs/api/web/categories.md 与 docs/schema/dish_attribute_value.md）：
 * <ol>
 *   <li><b>别名面</b>：一切读写都转发到 {@code DishAttributeAdminService} 的取值能力，且维度恒为系统维度；</li>
 *   <li><b>字段收敛</b>：出参只保留 id / label / order / dishCount / updatedAt（无机器键）；</li>
 *   <li><b>存在性校验</b>：`requireExists` 只认「属于系统维度」的取值 ID。</li>
 * </ol>
 */
class DishCategoryAdminServiceImplTest {

    private static final long SYSTEM_DIMENSION_ID = 5L;

    private final DishAttributeAdminService attributeAdminService = mock(DishAttributeAdminService.class);

    private DishCategoryAdminServiceImpl service() {
        when(attributeAdminService.systemDimensionId()).thenReturn(SYSTEM_DIMENSION_ID);
        return new DishCategoryAdminServiceImpl(attributeAdminService);
    }

    private static DishValueAdminVO value(Long id, String label, int order, long dishCount) {
        DishValueAdminVO vo = new DishValueAdminVO();
        vo.setId(id);
        vo.setDimensionId(SYSTEM_DIMENSION_ID);
        vo.setLabel(label);
        vo.setOrder(order);
        vo.setDishCount(dishCount);
        return vo;
    }

    @Test
    @DisplayName("create：转发到系统维度下的取值登记，并把出参收敛为种类字段")
    void create_delegatesToSystemDimension() {
        when(attributeAdminService.createValue(eq(SYSTEM_DIMENSION_ID), eq("面食粉类")))
                .thenReturn(value(9L, "面食粉类", 7, 0L));

        var vo = service().create("面食粉类");

        verify(attributeAdminService).createValue(SYSTEM_DIMENSION_ID, "面食粉类");
        assertThat(vo.getId()).isEqualTo(9L);
        assertThat(vo.getLabel()).isEqualTo("面食粉类");
        assertThat(vo.getDishCount()).isZero();
    }

    @Test
    @DisplayName("rename：转发到系统维度下的取值改名（改名免费，零菜品迁移）")
    void rename_delegatesToSystemDimension() {
        service().rename(3L, "面食粉面类");

        verify(attributeAdminService).updateValue(SYSTEM_DIMENSION_ID, 3L, "面食粉面类");
    }

    @Test
    @DisplayName("sort：转发到系统维度下的取值排序（全量行提交）")
    void sort_delegatesToSystemDimension() {
        List<SortItem> items = List.of(sortItem(3L, 1), sortItem(4L, 2));

        service().sort(items);

        verify(attributeAdminService).sortValues(SYSTEM_DIMENSION_ID, items);
    }

    private static SortItem sortItem(Long id, int order) {
        SortItem item = new SortItem();
        item.setId(id);
        item.setOrder(order);
        return item;
    }

    @Test
    @DisplayName("requireExists：取值不属于系统维度 → 400（A3 菜品保存的 mealTypeId 校验）")
    void requireExists_notInSystemDimension_rejected400() {
        when(attributeAdminService.labelByIdForDimension(SYSTEM_DIMENSION_ID))
                .thenReturn(Map.of(3L, "面食粉类"));

        assertThatThrownBy(() -> service().requireExists(404L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        // null 同款拦截，且不查字典
        assertThatThrownBy(() -> service().requireExists(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("requireExists：取值属于系统维度 → 通过")
    void requireExists_inSystemDimension_passes() {
        when(attributeAdminService.labelByIdForDimension(SYSTEM_DIMENSION_ID))
                .thenReturn(Map.of(3L, "面食粉类"));

        service().requireExists(3L);

        // 只查该维度下的取值（轻量），不做任何写入
        verify(attributeAdminService, never()).createValue(any(), any());
    }

    @Test
    @DisplayName("listAll：按系统维度取值出参映射（无机器键字段）")
    void listAll_mapsSystemDimensionValues() {
        when(attributeAdminService.listValues(SYSTEM_DIMENSION_ID))
                .thenReturn(List.of(value(3L, "面食粉类", 3, 2L), value(1L, "套餐盖饭", 1, 0L)));

        var list = service().listAll();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getId()).isEqualTo(3L);
        assertThat(list.get(0).getDishCount()).isEqualTo(2L);
        assertThat(list.get(1).getLabel()).isEqualTo("套餐盖饭");
    }
}
