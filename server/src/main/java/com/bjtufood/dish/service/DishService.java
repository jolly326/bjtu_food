package com.bjtufood.dish.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.dish.dto.HotSearchVO;

import java.util.List;

/**
 * 菜品服务接口
 * <p>
 * 菜品展示、搜索、管理、统计相关业务逻辑。
 * 评价模块通过事件机制通知本模块更新评分。
 */
public interface DishService {

    // ==================== 公开接口 ====================

    /**
     * 菜品列表查询（分页+筛选+排序）
     * <p>
     * 支持参数：keyword, canteenId, stallId, tag, minPrice, maxPrice, sortBy, sortOrder
     * 排序：sortBy=heat 时按综合热度（浏览量*1 + 评价数*100 + 评分*20）降序；未传 sortBy 时按评价数、评分降序
     * 公开接口只查 status=on 的菜品
     *
     * @param req 查询参数
     * @return 分页菜品列表（DishVO 含档口/食堂名称）
     */
    IPage<DishVO> listDishes(DishQueryReq req);

    /**
     * 获取热门菜品 TOP10
     * <p>
     * 按评价数降序、评分次之（rating_count DESC, avg_rating DESC），取前10条
     *
     * @return 热门菜品列表
     */
    List<DishVO> getHotDishes();

    /**
     * 获取热门菜品（支持按用户位置距离加权排序，首页推荐联动定位；可控制返回条数）
     *
     * @param lat   用户纬度（GCJ-02，可为 null）
     * @param lng   用户经度（GCJ-02，可为 null）
     * @param limit 返回条数（可为 null，默认 TOP10）
     * @return 热门菜品列表（近食堂菜品优先）
     */
    List<DishVO> getHotDishes(java.math.BigDecimal lat, java.math.BigDecimal lng, Integer limit);

    /**
     * 获取今日上新菜品
     * <p>
     * 按创建时间降序排列，取前8条（status=on）
     *
     * @return 上新菜品列表
     */
    List<DishVO> getNewDishes();

    /**
     * 获取限时活动菜品
     * <p>
     * 返回有活动特价的菜品，带活动价和截止时间
     *
     * @return 活动菜品列表
     */
    List<DishVO> getPromotionDishes();

    /**
     * 获取菜品详情
     * <p>
     * 如果请求已登录，会在响应中附加：
     * - hasReviewed：当前用户是否评价过该菜
     *
     * @param id     菜品ID
     * @param userId 当前用户ID（未登录可为null）
     * @return 菜品详情
     * @throws com.bjtufood.common.exception.BusinessException 菜品不存在
     */
    DishVO getDishDetail(Long id, Long userId);

    /**
     * 「猜你喜欢」推荐菜品（公开，无需登录；登录态个性化更强）
     * <p>
     * 仅对 audit_status=approved 且上架菜品生效，按热度分降序分页；
     * 支持 excludeIds 排除前端已展示项；未登录/无浏览历史时按纯热度（等同于热门弱化版）。
     *
     * @param page       页码
     * @param pageSize   每页条数
     * @param excludeIds 排除的菜品ID（逗号分隔，可选）
     * @param userId     当前登录用户ID（可选，用于个性化加权）
     * @return 分页推荐菜品
     */
    IPage<DishVO> recommendDishes(int page, int pageSize, String excludeIds, Long userId);

    /**
     * 增加菜品浏览量
     * <p>
     * 防刷机制：同一用户同一菜品 5 分钟内只计 1 次
     *
     * @param dishId 菜品ID
     * @param userId 当前用户ID
     */
    void addViewCount(Long dishId, Long userId);

    // ==================== 一期新增：搜索 / 发现页公开接口 ====================

    /**
     * 热搜词条 TOP10
     * <p>
     * 一期限定：无真实搜索词埋点，基于菜品综合热度派生热门词条；
     * heat 为该词条的热度分（view_count*1 + rating_count*5*20 + avg_rating*20）。
     *
     * @return 热搜词条列表（keyword=菜品名, heat=热度分）
     */
    List<HotSearchVO> hotSearch();

    /**
     * 新晋黑马 TOP10
     * <p>
     * 取近 14 天新上架且热度增速高的菜品，按 (rating_count*20 + view_count) 降序。
     *
     * @return 菜品列表
     */
    List<DishVO> rising();

    // ==================== 管理端接口（管理员） ====================

    /**
     * 查询全部菜品列表（含已下架），返回带完整图片 URL 的 VO（分页）
     *
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数（上限由 PageUtil 约束）
     * @return 分页后台菜品 VO
     */
    IPage<DishAdminVO> listAllForAdmin(int page, int pageSize);

    /**
     * 新增菜品
     *
     * @param req     菜品信息
     */
    void addDish(DishAdminReq req);

    /**
     * 编辑菜品
     *
     * @param id  菜品ID
     * @param req 菜品信息
     * @throws com.bjtufood.common.exception.BusinessException 菜品不存在
     */
    void updateDish(Long id, DishAdminReq req);

    /**
     * 删除菜品
     * <p>
     * 物理删除菜品，并级联清理关联评价与评价「有用」标记（review_useful）。
     *
     * @param id 菜品ID
     */
    void deleteDish(Long id);

    // ==================== 评分更新（事件驱动） ====================

    /**
     * 重新计算菜品平均评分
     * <p>
     * 由 RatingUpdateListener 在评价提交事件后调用
     *
     * @param dishId 菜品ID
     */
    void recalcAvgRating(Long dishId);
}
