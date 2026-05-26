package com.bjtufood.dish.event;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.review.event.ReviewSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 评分更新事件监听器
 * <p>
 * 监听 ReviewSubmittedEvent（评价提交事件），在评价事务提交后：
 * 1. 重新计算菜品的平均评分（AVG）
 * 2. 更新菜品表的 rating_count 和 avg_rating 字段
 * <p>
 * 位于 dish 模块中，对 review 模块无任何依赖（仅依赖事件对象）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RatingUpdateListener {

    /**
     * 处理评价提交事件，更新菜品评分
     * <p>
     * 处理流程：
     * 1. 收到事件，获取 dishId
     * 2. 从 review 表查询该菜品的当前平均评分（排除已删除/已隐藏）
     * 3. 更新 dish 表的 avg_rating 和 rating_count 字段
     * <p>
     * 触发条件：评价提交事务成功提交后（AFTER_COMMIT）
     *
     * @param event 评价提交事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onReviewSubmitted(ReviewSubmittedEvent event) {
        // TODO: 实现评分更新逻辑
        // 1. 从 event 获取 dishId
        // 2. 查询：SELECT AVG(rating), COUNT(*) FROM review
        //    WHERE dish_id = ? AND is_deleted = 0 AND is_hidden = 0
        // 3. 更新：UPDATE dish SET avg_rating = ?, rating_count = ? WHERE id = ?
        log.info("收到评价提交事件，菜品ID: {}, 评分: {}", event.getDishId(), event.getRating());
        // throw new BusinessException("实现评分更新逻辑");
    }
}
