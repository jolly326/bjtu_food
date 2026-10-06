package com.bjtufood.dish.event;

import com.bjtufood.dish.service.DishService;
import com.bjtufood.review.event.ReviewSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 评分聚合监听器：评价提交、事务提交后重算菜品平均评分（轻量写）。
 * <p>
 * 🔴 <b>同步执行</b>（刻意不加 {@code @Async}）：重算在事务提交后、<b>提交请求返回前</b>完成，
 * 保证「<b>提交评价者本人立即读到新值</b>」—— 详情页提交成功后重拉详情
 * （{@code useDishReviewComposer.onReviewSubmitted → fetchDetail}），若异步则可能读到旧分数
 * （口径见 {@code docs/产品设计评审/P0-2} §7 决议）。
 * <p>
 * 代价：写评价响应包含一次单菜品聚合 + 一次更新（轻量写），MVP 量级下不构成瓶颈，
 * 与「本人视角强一致」的收益相比可接受；失败仅记录告警日志，不回滚业务写，
 * 避免评分聚合异常影响评价提交链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RatingUpdateListener {

    private final DishService dishService;

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
