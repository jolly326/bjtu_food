package com.bjtufood.favorite.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 收藏状态变更事件
 * <p>
 * 当用户收藏或取消收藏菜品时，由 FavoriteService 发布此事件。
 * Dish 模块的 CollectCountListener 监听此事件，同步更新菜品收藏量。
 * <p>
 * isAdd=true 表示新增收藏 → collect_count +1
 * isAdd=false 表示取消收藏 → collect_count -1
 */
@Getter
public class FavoriteChangedEvent extends ApplicationEvent {

    /** 被操作的菜品ID */
    private final Long dishId;

    /** true=新增收藏, false=取消收藏 */
    private final boolean isAdd;

    /**
     * @param source 事件源
     * @param dishId 菜品ID
     * @param isAdd  true=收藏, false=取消
     */
    public FavoriteChangedEvent(Object source, Long dishId, boolean isAdd) {
        super(source);
        this.dishId = dishId;
        this.isAdd = isAdd;
    }
}
