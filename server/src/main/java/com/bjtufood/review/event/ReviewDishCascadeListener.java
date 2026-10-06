package com.bjtufood.review.event;

import com.bjtufood.dish.event.DishDeletedEvent;
import com.bjtufood.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 菜品删除 → 评价级联清理（P0-1 跨域写侧解耦：dish 域发 {@code DishDeletedEvent}，
 * 评价行由本域自行删除）。
 * <p>
 * 无 {@code @TransactionalEventListener}：监听器在发布者（deleteDish）事务内同步执行，
 * 级联失败与菜品删除一并回滚（事务边界与日志口径固定如此）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewDishCascadeListener {

    private final ReviewService reviewService;

    @EventListener
    public void onDishDeleted(DishDeletedEvent event) {
        int affected = reviewService.deleteByDishId(event.dishId());
        if (affected > 0) {
            log.info("菜品删除级联清理评价 dishId={} affected={}", event.dishId(), affected);
        }
    }
}
