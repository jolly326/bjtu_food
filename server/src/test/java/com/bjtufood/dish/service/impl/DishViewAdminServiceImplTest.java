package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.dish.dto.DishViewCondition;
import com.bjtufood.dish.dto.DishViewCreateReq;
import com.bjtufood.dish.dto.DishViewUpdateReq;
import com.bjtufood.dish.entity.DishFilterView;
import com.bjtufood.dish.mapper.DishFilterViewMapper;
import com.bjtufood.dish.service.DishViewCatalog;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * A6 视图管理的契约单测（口径见 docs/api/web/views.md 与 docs/schema/dish_filter_view.md）：
 * <ol>
 *   <li><b>条件入库</b>：入参条件经白名单校验后序列化落 `conditions`（`[]` = 不筛选，不落 NULL）；</li>
 *   <li><b>排序口径</b>：白名单外的 `sortKind` → `400`；</li>
 *   <li><b>护栏</b>：不允许停用 / 删除「最后一个启用的视图」；</li>
 *   <li><b>保存即生效</b>：一切写入口都显式失效视图目录缓存。</li>
 * </ol>
 */
class DishViewAdminServiceImplTest {

    private DishFilterViewMapper viewMapper;
    private DishViewCatalog viewCatalog;
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
        service = new DishViewAdminServiceImpl(viewMapper, viewCatalog);
    }

    private static DishFilterView row(Long id, String label, String conditions, String sortKind,
                                     int order, boolean enabled) {
        DishFilterView v = new DishFilterView();
        v.setId(id);
        v.setLabel(label);
        v.setConditions(conditions);
        v.setSortKind(sortKind);
        v.setSortOrder(order);
        v.setEnabled(enabled);
        return v;
    }

    private static DishViewCreateReq createReq(String label, List<DishViewCondition> conditions, String sortKind) {
        DishViewCreateReq req = new DishViewCreateReq();
        req.setLabel(label);
        req.setConditions(conditions);
        req.setSortKind(sortKind);
        return req;
    }

    private static DishViewUpdateReq updateReq(String label, boolean enabled,
                                               List<DishViewCondition> conditions, String sortKind) {
        DishViewUpdateReq req = new DishViewUpdateReq();
        req.setLabel(label);
        req.setEnabled(enabled);
        req.setConditions(conditions);
        req.setSortKind(sortKind);
        return req;
    }

    private static DishViewCondition mealTypeIs(String valueId) {
        DishViewCondition c = new DishViewCondition();
        c.setField("mealTypeId");
        c.setOp("=");
        c.setValue(valueId);
        return c;
    }

    @Test
    @DisplayName("create：条件序列化落库、默认启用排最后，并失效视图缓存")
    void create_serializesConditionsAndEnables() {
        when(viewMapper.selectList(any())).thenReturn(List.of(row(1L, "为你推荐", "[]", "random", 1, true)));
        when(viewMapper.selectById(any())).thenReturn(row(2L, "面食粉类", "[]", "random", 2, true));

        service.create(createReq("面食粉类", List.of(mealTypeIs("7")), "random"));

        var captor = org.mockito.ArgumentCaptor.forClass(DishFilterView.class);
        verify(viewMapper).insert(captor.capture());
        DishFilterView inserted = captor.getValue();
        assertThat(inserted.getLabel()).isEqualTo("面食粉类");
        assertThat(inserted.getConditions()).contains("mealTypeId").contains("\"7\"");
        assertThat(inserted.getSortKind()).isEqualTo("random");
        assertThat(inserted.getSortOrder()).isEqualTo(2);
        assertThat(inserted.getEnabled()).isTrue();
        verify(viewCatalog).invalidateViews();
    }

    @Test
    @DisplayName("create：空条件落 []（列 NOT NULL），排序口径非白名单 → 400")
    void create_emptyConditionsAndIllegalSortKind() {
        when(viewMapper.selectList(any())).thenReturn(List.of());
        when(viewMapper.selectById(any())).thenReturn(row(2L, "新视图", "[]", "random", 1, true));

        service.create(createReq("新视图", null, "random"));
        var captor = org.mockito.ArgumentCaptor.forClass(DishFilterView.class);
        verify(viewMapper).insert(captor.capture());
        assertThat(captor.getValue().getConditions()).isEqualTo("[]");

        assertThatThrownBy(() -> service.create(createReq("坏排序", List.of(), "heat")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    @DisplayName("update：四字段整体替换（含条件与排序口径），并失效缓存")
    void update_replacesAllFields() {
        when(viewMapper.selectById(1L)).thenReturn(row(1L, "面食", "[]", "random", 1, true));

        service.update(1L, updateReq("面食粉类", true, List.of(mealTypeIs("7")), "priceAsc"));

        var captor = org.mockito.ArgumentCaptor.forClass(DishFilterView.class);
        verify(viewMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLabel()).isEqualTo("面食粉类");
        assertThat(captor.getValue().getConditions()).contains("mealTypeId");
        assertThat(captor.getValue().getSortKind()).isEqualTo("priceAsc");
        verify(viewCatalog).invalidateViews();
    }

    @Test
    @DisplayName("update：条件字段不在白名单 → 400，不写库")
    void update_illegalCondition_rejected400() {
        when(viewMapper.selectById(1L)).thenReturn(row(1L, "面食", "[]", "random", 1, true));
        DishViewCondition ghost = new DishViewCondition();
        ghost.setField("mealType");
        ghost.setOp("=");
        ghost.setValue("noodle");

        assertThatThrownBy(() -> service.update(1L, updateReq("面食", true, List.of(ghost), "random")))
                .isInstanceOf(BusinessException.class);
        verify(viewMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("update：停用「最后一个启用的视图」→ 400")
    void update_disableLastEnabled_rejected400() {
        when(viewMapper.selectById(1L)).thenReturn(row(1L, "为你推荐", "[]", "random", 1, true));
        when(viewMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.update(1L, updateReq("为你推荐", false, List.of(), "random")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(ex).hasMessageContaining("最后一个启用的视图"));
        verify(viewMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("update：视图不存在 → 4001")
    void update_missing_rejected4001() {
        when(viewMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(404L, updateReq("任意", true, List.of(), "random")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
    }

    @Test
    @DisplayName("delete：删除启用视图前校验「最后一个启用的视图」；删除停用视图直接放行")
    void delete_guardsLastEnabled() {
        when(viewMapper.selectById(1L)).thenReturn(row(1L, "为你推荐", "[]", "random", 1, true));
        when(viewMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(ex).hasMessageContaining("最后一个启用的视图"));
        verify(viewMapper, never()).deleteById(any(java.io.Serializable.class));

        // 停用视图（enabled=false）可直接删除
        when(viewMapper.selectById(2L)).thenReturn(row(2L, "旧视图", "[]", "random", 2, false));
        service.delete(2L);

        verify(viewMapper).deleteById((java.io.Serializable) 2L);
        verify(viewCatalog, org.mockito.Mockito.times(1)).invalidateViews();
    }

    @Test
    @DisplayName("listAll：回显条件 / 排序口径 / 匹配数（条件非法时回显 []，不抛错）")
    void listAll_echoesConditionsAndSortKind() {
        when(viewCatalog.all()).thenReturn(List.of(
                row(1L, "为你推荐", "[]", "random", 1, true),
                row(2L, "面食粉类", "[{\"field\":\"mealTypeId\",\"op\":\"=\",\"value\":\"7\"}]", "random", 2, true)));
        when(viewCatalog.matchedCount(any(DishFilterView.class))).thenReturn(3L);

        var list = service.listAll();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getConditions()).isEmpty();
        assertThat(list.get(1).getConditions()).singleElement()
                .satisfies(c -> assertThat(c.getField()).isEqualTo("mealTypeId"));
        assertThat(list.get(1).getSortKind()).isEqualTo("random");
        assertThat(list.get(1).getMatchedCount()).isEqualTo(3L);
    }
}
