package com.bjtufood.dish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.dish.entity.DishViewLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 菜品浏览明细日志 Mapper。
 *
 * <p><b>用途</b>：支撑「近 30 天浏览量」的滚动窗口统计（管理端菜品列表展示）。
 *
 * <p><b>🔴 浏览量不参与任何排序</b>：本表仅供**管理端展示**（菜品列表「近 30 天浏览」）。
 * 浏览量无上限，参与排序即可被低成本刷量霸榜 ⇒ 若后期引入热度排序，须先给浏览量加封顶
 * （见 {@code docs/schema/dish_view_log.md} §2.2）。
 *
 * <p><b>计数口径</b>：PV，每次成功获取菜品详情 INSERT 一行，<b>不去重</b>
 * （同一用户反复看同一道菜每次都计）。理由见 {@code docs/schema/dish_view_log.md} §2.2。
 *
 * @see com.bjtufood.dish.task.DishViewLogCleanupTask 30 天滚动窗口清理
 */
@Mapper
public interface DishViewLogMapper extends BaseMapper<DishViewLog> {

    /**
     * 批量查询多个菜品的「近 30 天浏览量」（管理端列表用）。
     *
     * <p><b>为什么是 {@code COUNT(*) GROUP BY} 而不是日聚合表 {@code SUM}</b>：
     * 明细表可指定任意时间窗（不限 30 天），且允许事后按其他维度二次聚合；
     * 日聚合一旦存错无法还原。
     *
     * <p><b>🔴 为什么返回 {@code List<Row>} 而非 {@code Map<Long, Long>}</b>：
     * MyBatis 返回 {@code Map} 时，{@code resultType=Map} 的键名受
     * {@code map-underscore-to-camel-case} 影响（实测取不到值），而 {@code @MapKey} + {@code resultMap}
     * 的值是**整行 {@code HashMap}**（非单值），泛型 {@code Map<Long, Long>} 会直接
     * {@code ClassCastException}。用 POJO 承载是行为最确定的写法，由 Service 侧转 Map。
     *
     * @param dishIds    菜品 ID 集合（调用方保证非空，空集应跳过调用）
     * @param daysBefore 回溯天数（30 = 近 30 天）
     * @return 每行 = 一个有浏览记录的菜品；**无浏览记录的菜品不出现在结果中**（调用方需补 0）
     */
    List<Row> countRecentViewsByDishIds(@Param("dishIds") Collection<Long> dishIds,
                                        @Param("daysBefore") int daysBefore);

    /**
     * 「近 N 天浏览量」聚合行（{@code COUNT(*) GROUP BY dish_id} 的单行承载）。
     *
     * <p>仅为规避 MyBatis 的 {@code Map} 返回类型歧义而存在，不作其他用途、不直接出参。
     */
    class Row {
        /** 菜品 ID */
        private Long dishId;
        /** 窗口内浏览次数 */
        private Long viewCount;

        public Long getDishId() {
            return dishId;
        }

        public void setDishId(Long dishId) {
            this.dishId = dishId;
        }

        public Long getViewCount() {
            return viewCount;
        }

        public void setViewCount(Long viewCount) {
            this.viewCount = viewCount;
        }
    }

    /**
     * 删除早于 {@code daysBefore} 天的浏览日志（滚动窗口清理）。
     *
     * @param daysBefore 保留天数（30）
     * @return 删除行数
     */
    int deleteOlderThan(@Param("daysBefore") int daysBefore);
}