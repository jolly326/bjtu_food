package com.bjtufood.review.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 评价提交事件
 * <p>
 * 当学生提交评价成功后，由 ReviewService 发布此事件。
 * Dish 模块的 RatingUpdateListener 监听此事件，触发菜品平均评分重算。
 * <p>
 * 通过事件解耦的好处：
 * - ReviewService 不需要知道是谁在处理评分更新
 * - DishModule 可以随时修改评分逻辑，不影响 ReviewService
 * - 后续新增模块（如积分模块）也可以监听此事件
 */
@Getter
public class ReviewSubmittedEvent extends ApplicationEvent {

    /** 被评价的菜品ID */
    private final Long dishId;

    /** 新提交的评分数值 */
    private final Integer rating;

    /**
     * @param source 事件源（通常传入 this）
     * @param dishId 菜品ID
     * @param rating 评分（1-5）
     */
    public ReviewSubmittedEvent(Object source, Long dishId, Integer rating) {
        super(source);
        this.dishId = dishId;
        this.rating = rating;
    }
}
