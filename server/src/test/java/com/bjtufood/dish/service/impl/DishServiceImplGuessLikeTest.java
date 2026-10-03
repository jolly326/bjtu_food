package com.bjtufood.dish.service.impl;

import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.service.DishAttributeAdminService;
import com.bjtufood.dish.service.DishCategoryAdminService;
import com.bjtufood.dish.service.DishViewCatalog;
import com.bjtufood.dish.service.DishAttributeCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 猜你喜欢（{@code guessLike}）的 seed 口径回归测试。
 * <p>
 * <b>契约</b>：端上传会话级 seed ⇒ 会话内稳定、冷启动重掷；<b>未传 seed</b> ⇒ 每次不同的随机序。
 * <p>
 * <b>为什么必须有这个测试</b>：后一条行为原先由 SQL 的 {@code ORDER BY RAND()} 兜底（全表排序，
 * 代价随行数增长且无法用索引）。现改为 Service 侧补一次性随机 seed、SQL 恒走 CRC32 稳定伪随机序——
 * 这是一次「实现变了、行为必须不变」的等价替换，只有断言「下传的 seed 非空且两次不同」才能锁住它；
 * 否则一旦有人把补 seed 的代码删掉，SQL 会收到空 seed ⇒ {@code CRC32(CONCAT('', '-', id))}
 * 退化成**固定顺序**（每次都同一批菜），功能测试全绿，用户侧却表现为「猜你喜欢永远不变」。
 */
class DishServiceImplGuessLikeTest {

    private final DishMapper dishMapper = mock(DishMapper.class);

    private DishServiceImpl service() {
        return new DishServiceImpl(dishMapper, mock(StallService.class), mock(ApplicationEventPublisher.class),
                mock(ImageUrlUtil.class), mock(DishAttributeCatalog.class),
                mock(DishAttributeAdminService.class), mock(DishCategoryAdminService.class),
                mock(DishViewCatalog.class));
    }

    @Test
    @DisplayName("传入 seed：原样下传（会话内稳定由端上 seed 保证）")
    void passesSeedThrough() {
        when(dishMapper.selectGuessLike(anyInt(), anyString())).thenReturn(List.of());

        service().guessLike("session-abc");

        ArgumentCaptor<String> seed = ArgumentCaptor.forClass(String.class);
        verify(dishMapper).selectGuessLike(anyInt(), seed.capture());
        assertThat(seed.getValue()).isEqualTo("session-abc");
    }

    @Test
    @DisplayName("未传 seed（null / 空白）：Service 侧补一次性随机 seed，两次调用互不相同")
    void fillsSeedWhenAbsent() {
        when(dishMapper.selectGuessLike(anyInt(), anyString())).thenReturn(List.of());
        DishServiceImpl svc = service();

        svc.guessLike(null);
        svc.guessLike("   ");

        ArgumentCaptor<String> seed = ArgumentCaptor.forClass(String.class);
        verify(dishMapper, times(2)).selectGuessLike(anyInt(), seed.capture());
        assertThat(seed.getAllValues()).allSatisfy(s -> assertThat(s).isNotBlank());
        assertThat(seed.getAllValues().get(0)).isNotEqualTo(seed.getAllValues().get(1));
    }
}
