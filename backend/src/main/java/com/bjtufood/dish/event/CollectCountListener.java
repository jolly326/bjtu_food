package com.bjtufood.dish.event;

import com.bjtufood.dish.service.DishService;
import com.bjtufood.favorite.event.FavoriteChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectCountListener {

    private final DishService dishService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFavoriteChanged(FavoriteChangedEvent event) {
        log.info("Favorite changed, dishId: {}, added: {}", event.getDishId(), event.isAdd());
        dishService.syncCollectCount(event.getDishId());
    }
}
