package com.bjtufood.dish.task;

import com.bjtufood.dish.mapper.DishMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DishRatingReconcileTask} 单元测试（D3）。
 * <p>
 * <b>为什么这些断言重要</b>：本任务是 {@code RatingUpdateListener} 丢事件后的<b>唯一兜底</b>——
 * {@code dish.avg_rating}/{@code rating_count} 是缓存列，一旦异步重算丢失就会永久漂移，
 * 并持续污染首页热度排序（{@code heatScoreExpr} 直接消费这两列）。原实现的处理只是
 * {@code log.error("需人工补偿")}，即把正确性寄托在人工上。
 * <p>
 * 锁定的行为：
 * <ul>
 *   <li><b>真的重算</b>——不能只查不写，否则对账是空转；</li>
 *   <li><b>游标推进</b>——分批时必须用上批末位 id 作为下批游标，否则死循环或反复重扫首批；</li>
 *   <li><b>空结果即终止</b>——查不到更多行必须停，否则空转死循环；</li>
 *   <li><b>单品失败不中断整批</b>——一个菜品失败不该让本次对账全部作废（否则大表上永远对不完）。</li>
 * </ul>
 * 被测类为纯 POJO：{@code @Transactional} 依赖 Spring 代理，单测中不生效。
 */
class DishRatingReconcileTaskTest {

    private final DishMapper dishMapper = mock(DishMapper.class);

    private DishRatingReconcileTask task() {
        return new DishRatingReconcileTask(dishMapper);
    }

    /**
     * 让「按游标取一批」依次返回给定各批，最后一批之后返回空列表（终止条件）。
     * <p>
     * 用 {@code thenReturn(a, b, empty)} 的<b>单次连续桩</b>实现「游标逐批推进」：
     * Mockito 会按调用次序依次吐出各返回值，末尾空列表使任务正常收尾而不进入死循环。
     * 注意不可再补一条独立的 {@code when(...).thenReturn(List.of())}——那会覆盖整个连续桩，
     * 使任何一批都取不到（表现为「游标永不推进」）。
     */
    private void stubBatches(List<Long>... batchesAndTerminator) {
        when(dishMapper.selectDishIdsWithRatings(anyLong(), anyInt()))
                .thenReturn(batchesAndTerminator[0], Arrays.copyOfRange(batchesAndTerminator, 1,
                        batchesAndTerminator.length));
    }

    @Test
    @DisplayName("对账：逐个重算每个有评分的菜品（不能只查不写——那是对账空转）")
    void recalculatesEveryDish() {
        stubBatches(List.of(1L, 2L), List.of(3L), List.of());

        task().reconcileRatings();

        verify(dishMapper).recalcRatingBySubquery(1L);
        verify(dishMapper).recalcRatingBySubquery(2L);
        verify(dishMapper).recalcRatingBySubquery(3L);
        verify(dishMapper, times(3)).recalcRatingBySubquery(anyLong());
    }

    @Test
    @DisplayName("对账：分批游标推进（下批游标 = 上批末位 id，否则死循环或反复重扫首批）")
    void advancesCursorByBatchTail() {
        stubBatches(List.of(10L, 20L), List.of(30L), List.of());

        task().reconcileRatings();

        InOrder order = inOrder(dishMapper);
        // 首批游标为 0（尚未处理任何行）
        order.verify(dishMapper).selectDishIdsWithRatings(eq(0L), anyInt());
        // 次批游标必须是首批末位 id（20），而不是 0 或 10
        order.verify(dishMapper).selectDishIdsWithRatings(eq(20L), anyInt());
        // 第三次取批（游标 30）返回空 ⇒ 正常终止
        order.verify(dishMapper).selectDishIdsWithRatings(eq(30L), anyInt());
    }

    @Test
    @DisplayName("对账：查不到更多行即终止（不得空转死循环）")
    void stopsWhenNoMoreRows() {
        when(dishMapper.selectDishIdsWithRatings(anyLong(), anyInt())).thenReturn(List.of());

        task().reconcileRatings();

        verify(dishMapper, never()).recalcRatingBySubquery(anyLong());
        // 空结果一次即终止：不应继续反复取批
        verify(dishMapper, times(1)).selectDishIdsWithRatings(anyLong(), anyInt());
    }

    @Test
    @DisplayName("对账：单个菜品重算失败不中断整批（否则大表上永远对不完）")
    void singleFailureDoesNotAbortBatch() {
        stubBatches(List.of(1L, 2L, 3L), List.of());
        doThrow(new RuntimeException("db down")).when(dishMapper).recalcRatingBySubquery(2L);

        task().reconcileRatings(); // 不抛异常即符合预期

        verify(dishMapper).recalcRatingBySubquery(1L);
        verify(dishMapper).recalcRatingBySubquery(2L);
        // 关键断言：失败点之后的批次仍被处理
        verify(dishMapper).recalcRatingBySubquery(3L);
    }

    @Test
    @DisplayName("对账：整体异常被内部消化（调度线程抛异常会导致该任务后续不再被触发）")
    void swallowsTopLevelFailure() {
        when(dishMapper.selectDishIdsWithRatings(anyLong(), anyInt()))
                .thenThrow(new RuntimeException("connection reset"));

        // 关键断言：异常必须被内部消化——调度线程抛异常会导致该任务后续不再被触发
        assertThatCode(() -> task().reconcileRatings()).doesNotThrowAnyException();

        verify(dishMapper, never()).recalcRatingBySubquery(anyLong());
    }
}