package com.bjtufood.dish.event;

import com.bjtufood.dish.service.DishService;
import com.bjtufood.review.event.ReviewSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 评分聚合监听器：评价提交、事务提交后异步重算菜品平均评分（轻量写）。
 * <p>
 * 使用 {@code @Async} 将聚合计算从主请求线程剥离，避免阻塞写评价响应；
 * 失败仅记录告警日志，不回滚业务写，避免评分聚合异常影响评价提交链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RatingUpdateListener {

    private final DishService dishService;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewSubmitted(ReviewSubmittedEvent event) {
        log.info("Review changed, dishId: {}, rating: {}", event.getDishId(), event.getRating());
        try {
            dishService.recalcAvgRating(event.getDishId());
        } catch (Exception e) {
            // 聚合失败需告警：评分漂移会影响首页排序与热门推荐
            log.error("[ALERT] 评分重算失败，需人工补偿 dishId={}", event.getDishId(), e);
        }
    }
}
