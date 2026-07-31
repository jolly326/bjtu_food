package com.bjtufood.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.entity.Review;
import org.apache.ibatis.annotations.Param;

/**
 * 评价 Mapper 接口
 * <p>
 * 基础 CRUD + 复杂查询（查询评价列表时需关联用户表获取昵称和头像）
 */
public interface ReviewMapper extends BaseMapper<Review> {

    IPage<ReviewVO> selectReviewPageByDishId(Page<?> page, @Param("dishId") Long dishId, @Param("sort") String sort);

    IPage<ReviewVO> selectReviewPageByUserId(Page<?> page, @Param("userId") Long userId, @Param("sort") String sort);
}
