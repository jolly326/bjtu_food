package com.bjtufood.dish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishListItemVO;
import com.bjtufood.dish.dto.GuessLikeVO;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.view.DishListQuery;
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
     * 出参为**列表专用** {@link DishListItemVO}（8 字段，D 项拆分）；
     * 取数条件与排序口径均由 {@link DishListQuery}（视图解析结果）决定：
     * keyword 三路模糊 / mealType 等值 / discountOnly 折扣，以及 sortKind 决定的 ORDER BY。
     */
    IPage<DishListItemVO> selectDishPage(Page<?> page, @Param("q") DishListQuery q);

    /**
     * 查询菜品详情（联表）——详情专用 {@link DishDetailVO}（11 字段，含 {@code attributes} JSON 原文）
     */
    DishDetailVO selectDishDetail(@Param("id") Long id);

    /**
     * 查询全部在售菜品的描述属性 JSON 原文（{@code dish.attributes}）——供编辑候选值
     * 「按维度汇总全库已用中文值」用（{@code GET /dishes/{id}/attributes} / 管理端维度字典）。
     *
     * @return 在售菜品 attributes JSON 串列表（NULL 行不返回）
     */
    List<String> selectAttributesJsonOnSale();

    /**
     * 查询全部菜品列表（含已下架），联表档口和食堂名称
     * <p>
     * 分页：菜品量增长后避免单次全表加载。分页上限由调用方 {@code PageUtil.normalize} 约束。
     */
    IPage<DishAdminVO> selectAllForAdmin(Page<DishAdminVO> page);

    /**
     * 猜你喜欢：抽取在售菜品名（原「热搜词条」，改名 + 语义变更）
     *
     * @param limit 返回条数（由 Service 侧常量传入，避免 SQL 内硬编码）
     * @param seed  会话随机种子（可选）；非空 ⇒ {@code CRC32(seed:ID)} 稳定伪随机序
     *              ；
     *              空 ⇒ 退回 {@code ORDER BY RAND()}（向后兼容未传 seed 的调用方）
     * @return 猜你喜欢词条列表（GuessLikeVO{name}）
     */
    List<GuessLikeVO> selectGuessLike(@Param("limit") int limit, @Param("seed") String seed);

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
     * 供 {@code GET /dishes/views} 字典下发使用：
     * **空类自动隐藏**——某大类在售菜品数为 0 时不下发；重新有菜后自动出现。
     * 标签文案与顺序由 {@code DishViewConst} 提供（单一真源），本查询只回答「哪些类目下当前有菜」。
     *
     * @return 在售菜品覆盖的大类枚举键（去重）
     */
    List<String> selectInStockMealTypes();
}
