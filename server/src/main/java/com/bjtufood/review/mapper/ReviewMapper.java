package com.bjtufood.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.review.dto.MyReviewVO;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.dto.StallAvgRatingVO;
import com.bjtufood.review.entity.Review;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 评价 Mapper 接口
 * <p>
 * 基础 CRUD + 复杂查询（查询评价列表时需关联用户表获取昵称和头像）
 */
public interface ReviewMapper extends BaseMapper<Review> {

    /**
     * 按菜品查询评价列表（公开列表；时间倒序、可见性过滤）。
     * <p>
     * 可见性规则：唯一判据 is_hidden=0。
     * 排序唯一为 created_at DESC（🔴 覆盖提交不刷新 created_at ⇒ 评价位置固定）。
     * 公开出参不含 dishId / dishName / isHidden（三者仅在「我的评价」返回），故本查询不选这三列。
     *
     * @param rating 按星级筛选（1~5）；{@code null} = 不过滤。🔴 **筛选参与分页**
     *               （{@code WHERE} 在 {@code LIMIT/OFFSET} 之前生效）⇒ 端上切筛选必须重置 page=1。
     */
    IPage<ReviewVO> selectReviewPageByDishId(Page<?> page,
                                           @Param("dishId") Long dishId,
                                           @Param("rating") Integer rating);

    /**
     * 按用户查询「我的评价」列表（本人视角）。
     * <p>
     * 对评价全集按 user_id 过滤拆分；可见性与公开列表同口径：仅返回 is_hidden=0——
     * 被管理员隐藏的评价不对客户端（含作者本人）返回。
     * 排序：固定时间倒序。dishId 可选过滤（详情页判定「我是否已评价」）。
     */
    IPage<MyReviewVO> selectReviewPageByUserId(Page<?> page, @Param("userId") Long userId, @Param("dishId") Long dishId);

    /**
     * 批量计算多个档口下所有菜品评价的平均分（星级 1-5）。
     * <p>
     * 用于消除逐档口 N+1 查询：一次查询返回 stall_id → avg(rating) 的映射。无评价的档口不会出现在结果中。
     *
     * @param stallIds 档口ID集合
     * @return 每行含 stallId、avgRating（可能为 null）
     */
    List<StallAvgRatingVO> selectAvgRatingByStallIds(@Param("stallIds") Collection<Long> stallIds);

}
