package com.bjtufood.dish.service.impl;

import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.mapper.DishViewLogMapper;
import com.bjtufood.dish.service.DishAttributeAdminService;
import com.bjtufood.dish.service.DishAttributeCatalog;
import com.bjtufood.dish.service.DishCategoryAdminService;
import com.bjtufood.dish.service.DishViewCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@code listDishes} 的 keyword 长度上限回归测试（契约：docs/api/client/dishes.md「≤30 字符，超限 → 400」）。
 * <p>
 * 端上搜索框 {@code maxlength=30}；后端同口径校验，防直调超长入参打到三路 LIKE 模糊匹配。
 */
class DishServiceImplKeywordLimitTest {

    private final DishMapper dishMapper = mock(DishMapper.class);

    private DishServiceImpl service() {
        return new DishServiceImpl(dishMapper, mock(DishViewLogMapper.class), mock(StallService.class),
                mock(ApplicationEventPublisher.class),
                mock(ImageUrlUtil.class), mock(DishAttributeCatalog.class),
                mock(DishAttributeAdminService.class), mock(DishCategoryAdminService.class),
                mock(DishViewCatalog.class));
    }

    @Test
    @DisplayName("keyword 超 30 字 → 400「关键词不能超过 30 字」，不触达视图解析与查询")
    void rejectsKeywordOverLimit() {
        DishQueryReq req = new DishQueryReq();
        req.setKeyword("超".repeat(31));

        assertThatThrownBy(() -> service().listDishes(req))
                .isInstanceOf(BusinessException.class)
                .hasMessage("关键词不能超过 30 字");
        verifyNoInteractions(dishMapper);
    }

    @Test
    @DisplayName("keyword 恰 30 字：通过长度校验（mock 视图缺失 → 按「筛选视图不合法」流转，证明未触发长度拦截）")
    void allowsKeywordAtLimit() {
        DishQueryReq req = new DishQueryReq();
        req.setKeyword("菜".repeat(30));

        assertThatThrownBy(() -> service().listDishes(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("筛选视图不合法");
    }
}
