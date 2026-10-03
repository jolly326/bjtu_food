package com.bjtufood.dish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.dish.dto.DishAdminListItemVO;
import com.bjtufood.dish.dto.DishAdminListQuery;
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
     * 取数条件与排序口径均由 {@link DishListQuery}（**视图行**的解析结果）决定：
     * keyword 三路模糊 / `conditions`（字段白名单 + 参数化，见 DishViewConditions），
     * 以及 sortKind 决定的 ORDER BY（7 种口径）。
     */
    IPage<DishListItemVO> selectDishPage(Page<?> page, @Param("q") DishListQuery q);

    /**
     * 查询菜品详情（联表）——详情专用 {@link DishDetailVO}（11 字段，含 {@code attributes} JSON 原文）
     */
    DishDetailVO selectDishDetail(@Param("id") Long id);

    /**
     * 查询全部在售菜品的描述属性 JSON 原文（{@code dish.attributes}）——供编辑候选值
     * 「按维度汇总全库已用中文值」用（{@code GET /dishes/{id}/attributes} / 管理端维度字典）。
     * <p>
     * <b>为何带 limit 且按 id 排序</b>：本查询是读路径上唯一「行数决定返回体积」的查询
     * （每行一段 attributes JSON，全部经网络回传后在内存里解析聚合），不设上限即随菜品量线性膨胀，
     * 是内存与耗时的无界来源。加上限后：① 行为确定（同一数据多次调用截断点一致，不会时多时少）；
     * ② 上限只可能影响「参考候选的完整度」——候选值在契约里<u>仅为参考、不构成写入约束</u>
     * （见 {@code DishAttributeEditVO#options}），故截断不会让任何写入变错。
     *
     * @param limit 最多返回的行数（上限由 {@code DishAttributeCatalog} 侧常量给出）
     * @return 在售菜品 attributes JSON 串列表（NULL 行不返回；按 id 升序保证截断点稳定）
     */
    // 方法下线（2026-10-03，A4 落地）：selectAttributesJsonOnSale 的原消费方是
    // DishAttributeCatalog 的「扫全库 attributes 聚合候选值」；候选值改由 dish_attribute_value 字典
    // 直供后本方法**全仓零消费**，已连同 XML 映射一并删除（NON_NULL 语义的读路径不再需要它）。

    /**
     * 查询全部菜品列表（含已下架），联表档口和食堂名称
     * <p>
     * 分页：菜品量增长后避免单次全表加载。分页上限由调用方 {@code PageUtil.normalize} 约束。
     */
    IPage<DishAdminListItemVO> selectAdminListPage(Page<DishAdminListItemVO> page, @Param("q") DishAdminListQuery q);

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
    // 方法下线（2026-10-03，A6 落地）：selectInStockMealTypes 的原消费方是「常量视图 + 在售大类集合」
    // 驱动的 GET /dishes/views 空类过滤；视图改表驱动后，下发改由 DishViewCatalog.visible() 按
    // **条件匹配数**判定（口径更准：支持任意条件，不只是大类），本方法全仓零消费，已连同 XML 一并删除。

    /**
     * 列出「有评价」的菜品 ID（评分对账用，D3）。
     * <p>
     * 只取 {@code rating_count > 0} 的行：零评价菜品的 {@code avg_rating}/{@code rating_count}
     * 按口径本就恒为 NULL/0，无需参与对账，可显著缩小扫描面。
     * <p>
     * 分批游标推进（{@code id > lastId ORDER BY id LIMIT n}）而非 OFFSET 分页：
     * 对账期间若菜品被新增/删除，OFFSET 会因行位移而漏行或重复行；游标法不受影响。
     *
     * @param lastId  游标：只取 id 大于该值的行（首页传 0）
     * @param limit   本批最多返回行数（由调用方给出，控制单批内存与事务时长）
     * @return 菜品 ID 升序列表
     */
    List<Long> selectDishIdsWithRatings(@Param("lastId") long lastId, @Param("limit") int limit);
}
