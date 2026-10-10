package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.dish.dto.DishViewAdminVO;
import com.bjtufood.dish.dto.DishViewCreateReq;
import com.bjtufood.dish.dto.DishViewUpdateReq;
import com.bjtufood.dish.entity.DishFilterView;
import com.bjtufood.dish.mapper.DishFilterViewMapper;
import com.bjtufood.dish.service.DishCategoryAdminService;
import com.bjtufood.dish.service.DishViewCatalog;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A6 视图管理的契约单测（口径见 docs/api/web/views.md 与 docs/schema/dish_filter_view.md）：
 * <ol>
 *   <li><b>归属种类</b>：入参 `categoryId` 非空必须命中种类字典（不存在 → `400`），null = 不按种类过滤；</li>
 *   <li><b>文案</b>：1~32 字、非空（`400`）；</li>
 *   <li><b>护栏</b>：不允许停用 / 删除「最后一个启用的视图」；</li>
 *   <li><b>保存即生效</b>：一切写入口都显式失效视图目录缓存。</li>
 * </ol>
 */
class DishViewAdminServiceImplTest {

    private DishFilterViewMapper viewMapper;
    private DishViewCatalog viewCatalog;
    private DishCategoryAdminService categoryAdminService;
    private DishViewAdminServiceImpl service;

    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(),
                DishViewAdminServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, DishFilterView.class);
    }

    @BeforeEach
    void setUp() {
        viewMapper = mock(DishFilterViewMapper.class);
        viewCatalog = mock(DishViewCatalog.class);
        categoryAdminService = mock(DishCategoryAdminService.class);
        service = new DishViewAdminServiceImpl(viewMapper, viewCatalog, categoryAdminService);
    }

    private static DishFilterView row(Long id, String name, Long categoryId, boolean enabled) {
        DishFilterView v = new DishFilterView();
        v.setId(id);
        v.setName(name);
        v.setCategoryId(categoryId);
        v.setEnabled(enabled);
        return v;
    }

    private static DishViewCreateReq createReq(String name, Long categoryId) {
        DishViewCreateReq req = new DishViewCreateReq();
        req.setName(name);
        req.setCategoryId(categoryId);
        return req;
    }

    private static DishViewUpdateReq updateReq(String name, boolean enabled, Long categoryId) {
        DishViewUpdateReq req = new DishViewUpdateReq();
        req.setName(name);
        req.setEnabled(enabled);
        req.setCategoryId(categoryId);
        return req;
    }

    @Test
    @DisplayName("create：默认启用、归属种类透传（null = 不按种类过滤），并失效视图缓存")
    void create_persistsRowAndInvalidates() {
        when(viewMapper.selectById(any())).thenReturn(row(2L, "面食粉类", 7L, true));

        service.create(createReq("面食粉类", 7L));

        ArgumentCaptor<DishFilterView> captor = ArgumentCaptor.forClass(DishFilterView.class);
        verify(viewMapper).insert(captor.capture());
        DishFilterView inserted = captor.getValue();
        assertThat(inserted.getName()).isEqualTo("面食粉类");
        assertThat(inserted.getCategoryId()).isEqualTo(7L);
        assertThat(inserted.getEnabled()).isTrue();
        verify(categoryAdminService).requireExists(7L);
        verify(viewCatalog).invalidateViews();
    }

    @Test
    @DisplayName("create：categoryId 为 null 跳过种类校验（聚合视角不按种类过滤）")
    void create_nullCategory_skipsCheck() {
        when(viewMapper.selectById(any())).thenReturn(row(2L, "为你推荐", null, true));

        service.create(createReq("为你推荐", null));

        ArgumentCaptor<DishFilterView> captor = ArgumentCaptor.forClass(DishFilterView.class);
        verify(viewMapper).insert(captor.capture());
        assertThat(captor.getValue().getCategoryId()).isNull();
        verify(categoryAdminService, never()).requireExists(any());
        verify(viewCatalog).invalidateViews();
    }

    @Test
    @DisplayName("create / update：归属种类不存在 → 400（requireExists 拦截），不写库")
    void create_unknownCategory_rejected400() {
        doThrow(new BusinessException("分类不存在：99")).when(categoryAdminService).requireExists(99L);

        assertThatThrownBy(() -> service.create(createReq("幽灵视图", 99L)))
                .isInstanceOf(BusinessException.class);
        verify(viewMapper, never()).insert(any(DishFilterView.class));
    }

    @Test
    @DisplayName("create：tab 文案空白 / 超 32 字 → 400")
    void create_invalidName_rejected400() {
        assertThatThrownBy(() -> service.create(createReq("  ", null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(createReq("x".repeat(33), null)))
                .isInstanceOf(BusinessException.class);
        verify(viewMapper, never()).insert(any(DishFilterView.class));
    }

    @Test
    @DisplayName("update：三字段整体替换（文案 / 启停 / 归属种类），并失效缓存")
    void update_replacesAllFields() {
        when(viewMapper.selectById(1L)).thenReturn(row(1L, "面食", 7L, true));

        service.update(1L, updateReq("面食粉类", true, 8L));

        ArgumentCaptor<DishFilterView> captor = ArgumentCaptor.forClass(DishFilterView.class);
        verify(viewMapper).updateById(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("面食粉类");
        assertThat(captor.getValue().getCategoryId()).isEqualTo(8L);
        assertThat(captor.getValue().getEnabled()).isTrue();
        verify(categoryAdminService).requireExists(8L);
        verify(viewCatalog).invalidateViews();
    }

    @Test
    @DisplayName("update：停用「最后一个启用的视图」→ 400")
    void update_disableLastEnabled_rejected400() {
        when(viewMapper.selectById(1L)).thenReturn(row(1L, "为你推荐", null, true));
        when(viewMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.update(1L, updateReq("为你推荐", false, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(ex).hasMessageContaining("最后一个启用的视图"));
        verify(viewMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("update：视图不存在 → 4001")
    void update_missing_rejected4001() {
        when(viewMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(404L, updateReq("任意", true, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
    }

    @Test
    @DisplayName("delete：删除启用视图前校验「最后一个启用的视图」；删除停用视图直接放行")
    void delete_guardsLastEnabled() {
        when(viewMapper.selectById(1L)).thenReturn(row(1L, "为你推荐", null, true));
        when(viewMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(ex).hasMessageContaining("最后一个启用的视图"));
        verify(viewMapper, never()).deleteById(any(java.io.Serializable.class));

        // 停用视图（enabled=false）可直接删除
        when(viewMapper.selectById(2L)).thenReturn(row(2L, "旧视图", null, false));
        service.delete(2L);

        verify(viewMapper).deleteById((java.io.Serializable) 2L);
        verify(viewCatalog, org.mockito.Mockito.times(1)).invalidateViews();
    }

    @Test
    @DisplayName("listAll：回显归属种类与匹配数")
    void listAll_echoesCategoryAndMatchedCount() {
        when(viewCatalog.all()).thenReturn(List.of(
                row(1L, "为你推荐", null, true),
                row(2L, "面食粉类", 7L, true)));
        when(viewCatalog.matchedCount(any(DishFilterView.class))).thenReturn(3L);

        List<DishViewAdminVO> list = service.listAll();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getCategoryId()).isNull();
        assertThat(list.get(1).getCategoryId()).isEqualTo(7L);
        assertThat(list.get(1).getMatchedCount()).isEqualTo(3L);
        assertThat(list.get(1).getName()).isEqualTo("面食粉类");
    }
}
