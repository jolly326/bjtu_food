package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.mapper.DishViewLogMapper;
import com.bjtufood.dish.service.DishAttributeAdminService;
import com.bjtufood.dish.service.DishAttributeCatalog;
import com.bjtufood.dish.service.DishCategoryAdminService;
import com.bjtufood.dish.service.DishViewCatalog;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code updateDish} 的 {@code attributes} 语义回归（契约见 docs/api/web/dishes.md）：
 * 「不传 ⇒ 不修改」与「传空对象 ⇒ 清空整列」必须是两个**可区分**的语义。
 * <p>
 * 判据只能取「请求带没带该字段」：空对象经序列化后同样得到 {@code null}，
 * 若按 JSON 判空，「清空」会被退化成「不修改」——管理端将永远无法清空菜品属性，且无任何报错。
 */
class DishServiceImplUpdateAttributesTest {

    /** 纯 Mockito 无 Spring 上下文，MyBatis-Plus 的 lambda 缓存需显式初始化 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(),
                DishServiceImplUpdateAttributesTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, Dish.class);
    }

    private final DishMapper dishMapper = mock(DishMapper.class);

    private DishServiceImpl service() {
        return new DishServiceImpl(dishMapper, mock(DishViewLogMapper.class), mock(StallService.class),
                mock(ApplicationEventPublisher.class),
                mock(ImageUrlUtil.class), mock(DishAttributeCatalog.class),
                mock(DishAttributeAdminService.class), mock(DishCategoryAdminService.class),
                mock(DishViewCatalog.class));
    }

    private static DishAdminReq req() {
        DishAdminReq req = new DishAdminReq();
        req.setName("红烧肉");
        req.setPrice(1200);
        req.setCategoryId(3L);
        req.setImages(List.of("/images/hongshaorou.jpg"));
        return req;
    }

    /** 取本次条件 UPDATE 的 SET 子句原文（用于断言「该列有没有被写」） */
    private static String setClause(LambdaUpdateWrapper<Dish> wrapper) {
        return String.valueOf(wrapper.getSqlSet()) + String.valueOf(wrapper.getSqlSegment());
    }

    private LambdaUpdateWrapper<Dish> capturedUpdate() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<Dish>> wrapper = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(dishMapper).update(isNull(), wrapper.capture());
        return wrapper.getValue();
    }

    @Test
    @DisplayName("attributes 传空对象 → 显式写 NULL（清空整列），不得退化成「不修改」")
    void emptyAttributesClearsColumn() {
        when(dishMapper.selectById(7L)).thenReturn(new Dish());
        DishAdminReq req = req();
        req.setAttributes(new HashMap<>());

        service().updateDish(7L, req);

        assertThat(setClause(capturedUpdate())).contains("attributes");
    }

    @Test
    @DisplayName("attributes 不传 → SET 中不含该列（不传即不动）")
    void absentAttributesLeavesColumnUntouched() {
        when(dishMapper.selectById(7L)).thenReturn(new Dish());

        service().updateDish(7L, req());

        assertThat(setClause(capturedUpdate())).doesNotContain("attributes");
    }
}
