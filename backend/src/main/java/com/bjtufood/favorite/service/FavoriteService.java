package com.bjtufood.favorite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.favorite.entity.Favorite;

import java.util.List;
import java.util.Map;

/**
 * 收藏服务接口
 * <p>
 * 收藏的增删查和批量操作。
 * 每次操作后发布 FavoriteChangedEvent，由 dish 模块监听更新收藏量。
 */
public interface FavoriteService {

    /**
     * 切换收藏状态
     * <p>
     * 如果已收藏则取消，未收藏则新增
     * 操作后发布 FavoriteChangedEvent
     *
     * @param userId 用户ID
     * @param dishId 菜品ID
     * @return true=已收藏, false=已取消
     */
    boolean toggle(Long userId, Long dishId);

    /**
     * 查询用户收藏的菜品列表（分页，返回完整菜品信息）
     *
     * @param userId   用户ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页收藏菜品列表
     */
    IPage<DishVO> listFavoriteDishes(Long userId, int page, int pageSize);

    /**
     * 批量收藏菜品
     * <p>
     * 供清单一键收藏功能使用。
     * 自动去重：已收藏的菜品跳过
     *
     * @param userId  用户ID
     * @param dishIds 菜品ID列表
     * @return 结果统计 { succeeded: 成功数, skipped: 跳过数 }
     */
    Map<String, Integer> batchCollect(Long userId, List<Long> dishIds);

    /**
     * 查询用户是否收藏了某菜品
     *
     * @param userId 用户ID
     * @param dishId 菜品ID
     * @return true=已收藏
     */
    boolean isFavorited(Long userId, Long dishId);

    /**
     * 查询用户对多个菜品的收藏状态
     * <p>
     * 用于前端批量展示收藏按钮状态
     *
     * @param userId  用户ID
     * @param dishIds 菜品ID列表
     * @return Map<dishId, isFavorited>
     */
    Map<Long, Boolean> batchCheckFavorited(Long userId, List<Long> dishIds);
}
