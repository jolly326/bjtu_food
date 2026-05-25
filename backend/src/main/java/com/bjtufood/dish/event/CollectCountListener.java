package com.bjtufood.dish.event;

import com.bjtufood.favorite.event.FavoriteChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 收藏量更新事件监听器
 * <p>
 * 监听 FavoriteChangedEvent（收藏状态变更事件），在收藏事务提交后：
 * - 收藏事件（isAdd=true）：dish.collect_count + 1
 * - 取消收藏事件（isAdd=false）：dish.collect_count - 1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectCountListener {

    /**
     * 处理收藏状态变更事件，同步更新菜品收藏量
     * <p>
     * 处理流程：
     * 1. 从事件中获取 dishId 和 isAdd（true=收藏, false=取消）
     * 2. 执行 UPDATE dish SET collect_count = collect_count + (1/-1) WHERE id = ?
     * <p>
     * 为了保证数据一致，直接通过 COUNT 查询计算：
     * UPDATE dish SET collect_count = (SELECT COUNT(*) FROM favorite WHERE dish_id = ?) WHERE id = ?
     *
     * @param event 收藏状态变更事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onFavoriteChanged(FavoriteChangedEvent event) {
        // TODO: 实现收藏量更新逻辑
        // 方案一：增量更新
        //   int delta = event.isAdd() ? 1 : -1;
        //   UPDATE dish SET collect_count = collect_count + ? WHERE id = ?
        //
        // 方案二：全量更新（更精确，推荐）
        //   UPDATE dish SET collect_count = (SELECT COUNT(*) FROM favorite WHERE dish_id = ?) WHERE id = ?
        log.info("收到收藏事件，菜品ID: {}, 操作: {}", event.getDishId(),
                event.isAdd() ? "收藏" : "取消收藏");
        // throw new BusinessException("实现收藏量更新逻辑");
    }
}
