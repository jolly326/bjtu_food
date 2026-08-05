package com.bjtufood.dish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.dish.dto.HotSearchVO;
import com.bjtufood.dish.dto.MyDishVO;
import com.bjtufood.dish.dto.RatingDistributionVO;
import com.bjtufood.dish.dto.SuggestionVO;
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
     * 查询热门菜品 TOP10（按用户位置距离加权排序）
     * <p>
     * 有坐标时：先按食堂距离升序（近的食堂菜品优先），热度（收藏/评分）作次级排序。
     * 无坐标食堂的菜品排最后。
     *
     * @param lat 用户纬度（GCJ-02，可为 null）
     * @param lng 用户经度（GCJ-02，可为 null）
     */
    List<DishVO> selectHotDishesByDistance(@Param("lat") java.math.BigDecimal lat, @Param("lng") java.math.BigDecimal lng);

    /**
     * 查询今日上新菜品 TOP8
     * <p>
     * 按创建时间降序，取前 8 条
     */
    List<DishVO> selectNewDishes();

    /**
     * 查询限时活动菜品 TOP4
     * <p>
     * 按标签筛选 promotion，取前 4 条
     */
    List<DishVO> selectPromotionDishes();

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
     */
    List<DishAdminVO> selectAllForAdmin();

    /**
     * 查询「我的发布」菜品列表（created_by = userId，可按审核状态过滤）
     */
    List<MyDishVO> selectMyDishes(@Param("userId") Long userId, @Param("auditStatus") String auditStatus);

    /**
     * 搜索联想（菜品 / 档口 / 食堂名混合，各取 TOP5）
     * <p>
     * 一期限定：无搜索词埋点表，按 keyword LIKE 匹配 name 派生联想建议。
     *
     * @param keyword 搜索关键词
     * @return 联想建议列表（SuggestionVO{type,id,name,image}）
     */
    List<SuggestionVO> selectSuggestions(@Param("keyword") String keyword);

    /**
     * 热搜词条 TOP10（基于菜品综合热度派生的热门词条，无真实搜索词埋点）
     *
     * @return 热搜词条列表（HotSearchVO{keyword,heat}）
     */
    List<HotSearchVO> selectHotSearch();

    /**
     * 新晋黑马 TOP10（近 14 天新上架且热度增速高的菜品）
     *
     * @return 菜品列表（DishVO）
     */
    List<DishVO> selectRising();
}
