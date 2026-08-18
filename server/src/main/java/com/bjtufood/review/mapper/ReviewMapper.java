package com.bjtufood.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.dto.StallAvgRatingDTO;
import com.bjtufood.review.entity.Review;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 评价 Mapper 接口
 * <p>
 * 基础 CRUD + 复杂查询（查询评价列表时需关联用户表获取昵称和头像）
 */
public interface ReviewMapper extends BaseMapper<Review> {

    IPage<ReviewVO> selectReviewPageByDishId(Page<?> page, @Param("dishId") Long dishId, @Param("sort") String sort, @Param("withImage") boolean withImage);

    IPage<ReviewVO> selectReviewPageByUserId(Page<?> page, @Param("userId") Long userId, @Param("sort") String sort);

    /**
     * 按档口查询评价列表。
     * <p>
     * review 表仅关联 dish_id（无 stall_id / canteen_id），档口/食堂维度的评价通过
     * review → dish(stall_id) 推导。只返回 is_hidden=0 的评价。
     */
    IPage<ReviewVO> selectReviewPageByStallId(Page<?> page, @Param("stallId") Long stallId, @Param("sort") String sort, @Param("withImage") boolean withImage);

    /**
     * 按食堂查询评价列表。
     * <p>
     * 通过 review → dish(stall_id) → stall(canteen_id) 推导。只返回 is_hidden=0 的评价。
     */
    IPage<ReviewVO> selectReviewPageByCanteenId(Page<?> page, @Param("canteenId") Long canteenId, @Param("sort") String sort, @Param("withImage") boolean withImage);

    /**
     * 计算某档口下所有菜品评价的平均分（星级 1-5）。
     * <p>
     * 通过 review → dish(stall_id) 推导；无评价返回 NULL。
     *
     * @param stallId 档口ID
     * @return 平均分，可能为 null
     */
    BigDecimal selectAvgRatingByStallId(@Param("stallId") Long stallId);

    /**
     * 批量计算多个档口下所有菜品评价的平均分（星级 1-5）。
     * <p>
     * 用于消除逐档口 N+1 查询：一次查询返回 stall_id → avg(rating) 的映射。无评价的档口不会出现在结果中。
     *
     * @param stallIds 档口ID集合
     * @return 每行含 stallId、avgRating（可能为 null）
     */
    List<StallAvgRatingDTO> selectAvgRatingByStallIds(@Param("stallIds") Collection<Long> stallIds);

    /**
     * 评价「有用」计数原子增减（并发安全：SET useful_count = useful_count ± delta，最小值 0）
     *
     * @param id    评价ID
     * @param delta +1 标记 / -1 取消
     * @return 影响行数
     */
    int changeUsefulCount(@Param("id") Long id, @Param("delta") int delta);

}
