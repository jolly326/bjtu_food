package com.bjtufood.dish.event;

import com.bjtufood.dish.service.DishService;
import com.bjtufood.review.event.ReviewSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RatingUpdateListener {

    private final DishService dishService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewSubmitted(ReviewSubmittedEvent event) {
        log.info("Review changed, dishId: {}, rating: {}", event.getDishId(), event.getRating());
        dishService.recalcAvgRating(event.getDishId());
    }
}
