package com.bjtufood.dish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.dish.dto.HotSearchVO;
import com.bjtufood.dish.dto.RatingDistributionVO;
import com.bjtufood.dish.entity.Dish;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜品 Mapper 接口
 * <p>
 * 基础 CRUD 由 MyBatis-Plus 自动实现。
 * 复杂查询方法在 DishMapper.xml 中定义（如多表关联查询、动态排序等）。
 */
public interface DishMapper extends BaseMapper<Dish> {

    /**
     * 分页查询菜品（联表：dish + stall + canteen）
     * <p>
     * 支持关键词、食堂ID、档口ID、价格区间、排序等筛选条件
     */
    IPage<DishVO> selectDishPage(Page<?> page, @Param("req") DishQueryReq req);

    /**
     * 查询菜品详情（联表）
     */
    DishDetailVO selectDishDetail(@Param("id") Long id);

    /**
     * 查询菜品评分分布
     * <p>
     * 按星级分组，统计各星级人数
     */
    List<RatingDistributionVO> selectRatingDistribution(@Param("dishId") Long dishId);

    /**
     * 查询全部菜品列表（含已下架），联表档口和食堂名称
     * <p>
     * 分页：菜品量增长后避免单次全表加载。分页上限由调用方 {@code PageUtil.normalize} 约束。
     */
    IPage<DishAdminVO> selectAllForAdmin(Page<DishAdminVO> page);

    /**
     * 热搜词条 TOP10（基于菜品综合热度派生的热门词条，无真实搜索词埋点）
     *
     * @return 热搜词条列表（HotSearchVO{keyword,heat}）
     */
    List<HotSearchVO> selectHotSearch();

    /**
     * 浏览量原子自增（并发安全：UPDATE ... SET view_count = view_count + 1）
     *
     * @param id 菜品ID
     * @return 影响行数（0=菜品不存在）
     */
    int increaseViewCount(@Param("id") Long id);

    /**
     * 评分聚合原子重算（并发安全：子查询 AVG/COUNT 后整体写回）
     * <p>
     * 仅统计未隐藏评价；avg_rating 保留 1 位小数。
     *
     * @param dishId 菜品ID
     * @return 影响行数
     */
    int recalcRatingBySubquery(@Param("dishId") Long dishId);

    /**
     * 查询「当前存在在售菜品」的菜品大类枚举键（去重）。
     * <p>
     * 供 {@code GET /dishes/meal-types} 字典下发使用（2026-09-21 §7.34）：
     * **空类自动隐藏**——某大类在售菜品数为 0 时不下发；重新有菜后自动出现。
     * 标签文案与顺序由 {@code MealTypeConst} 提供（单一真源），本查询只回答「哪些类目下当前有菜」。
     *
     * @return 在售菜品覆盖的大类枚举键（去重）
     */
    List<String> selectInStockMealTypes();
}
