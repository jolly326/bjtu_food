package com.bjtufood.dish.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.dish.entity.Dish;

import java.util.List;

/**
 * 菜品服务接口
 * <p>
 * 菜品展示、搜索、管理、统计相关业务逻辑。
 * 评价/收藏模块通过事件机制通知本模块更新评分和收藏数。
 */
public interface DishService {

    // ==================== 公开接口 ====================

    /**
     * 菜品列表查询（分页+筛选+排序）
     * <p>
     * 支持参数：keyword, canteenId, stallId, tag, minPrice, maxPrice, sortBy, sortOrder
     * 默认排序：综合热度（收藏量*3 + 浏览量*1 + 评价数*5）降序
     * 公开接口只查 status=on 的菜品
     *
     * @param req 查询参数
     * @return 分页菜品列表（DishVO 含档口/食堂名称）
     */
    IPage<DishVO> listDishes(DishQueryReq req);

    /**
     * 获取热门菜品 TOP10
     * <p>
     * 按收藏量降序排列，取前10条
     *
     * @return 热门菜品列表
     */
    List<DishVO> getHotDishes();

    /**
     * 获取菜品详情
     * <p>
     * 如果请求已登录，会在响应中附加：
     * - isFavorited：当前用户是否收藏该菜
     * - hasReviewed：当前用户是否评价过该菜
     *
     * @param id     菜品ID
     * @param userId 当前用户ID（未登录可为null）
     * @return 菜品详情
     * @throws com.bjtufood.common.exception.BusinessException 菜品不存在
     */
    DishVO getDishDetail(Long id, Long userId);

    /**
     * 增加菜品浏览量
     * <p>
     * 防刷机制：同一用户同一菜品 5 分钟内只计 1 次
     *
     * @param dishId 菜品ID
     * @param userId 当前用户ID
     */
    void addViewCount(Long dishId, Long userId);

    // ==================== 管理端接口（食堂管理员） ====================

    /**
     * 查询管理员所属档口的菜品列表
     * <p>
     * 食堂管理员只能看到自己档口的菜品
     *
     * @param stallId 管理员绑定的档口ID
     * @return 菜品列表（含已下架）
     */
    List<Dish> listByStallId(Long stallId);

    /**
     * 新增菜品
     *
     * @param stallId 所属档口ID
     * @param req     菜品信息
     */
    void addDish(Long stallId, DishAdminReq req);

    /**
     * 编辑菜品
     *
     * @param id  菜品ID
     * @param req 菜品信息
     * @throws com.bjtufood.common.exception.BusinessException 菜品不存在
     */
    void updateDish(Long id, DishAdminReq req);

    /**
     * 删除菜品（软删除）
     *
     * @param id 菜品ID
     */
    void deleteDish(Long id);

    // ==================== 评分/收藏量更新（事件驱动） ====================

    /**
     * 重新计算菜品平均评分
     * <p>
     * 由 RatingUpdateListener 在评价提交事件后调用
     *
     * @param dishId 菜品ID
     */
    void recalcAvgRating(Long dishId);

    /**
     * 同步菜品收藏量
     * <p>
     * 由 CollectCountListener 在收藏事件后调用
     *
     * @param dishId 菜品ID
     */
    void syncCollectCount(Long dishId);
}
