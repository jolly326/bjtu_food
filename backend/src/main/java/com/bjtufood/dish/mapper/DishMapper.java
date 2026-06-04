package com.bjtufood.dish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.DishVO;
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
     * 查询热门菜品 TOP10（联表）
     * <p>
     * 按收藏量降序，取前 10 条
     */
    List<DishVO> selectHotDishes();

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
}
