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
     * 排序：sortBy=heat 时按综合热度（d.view_count*1 + d.rating_count*5*20 + COALESCE(d.avg_rating,0)*20）降序；
     * 热度口径唯一真源见 DishMapper.xml 的 heatScoreExpr 片段（Q-109：Java 侧权重常量已删除，调整请直接改该 SQL）；
     * 未传 sortBy 时按评价数、评分降序
     * 公开接口只查 status=on 的菜品
     *
     * @param req 查询参数
     * @return 分页菜品列表（DishVO 含档口/食堂名称）
     */
    IPage<DishVO> listDishes(DishQueryReq req);

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
     * 增加菜品浏览量
     * <p>
     * 防刷机制（2026-09-14 §7.14 A）：
     * <ol>
     *   <li>同一用户同一菜品 5 分钟内只计 1 次；</li>
     *   <li>同一用户同一菜品<b>每天（自然日，Asia/Shanghai）只计 1 次</b>，
     *       当日重复上报幂等返回成功，既不自增 view_count 也不重复写 view_log。</li>
     * </ol>
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
     * heat 为该词条的热度分（d.view_count*1 + d.rating_count*5*20 + COALESCE(d.avg_rating,0)*20），
     * 与列表 heat 排序共用 DishMapper.xml 的 heatScoreExpr 片段（等价口径）。
     *
     * @return 热搜词条列表（keyword=菜品名, heat=热度分）
     */
    List<HotSearchVO> hotSearch();

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
