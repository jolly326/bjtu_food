package com.bjtufood.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.review.entity.Review;

/**
 * 评价 Mapper 接口
 * <p>
 * 基础 CRUD + 复杂查询（查询评价列表时需关联用户表获取昵称和头像）
 */
public interface ReviewMapper extends BaseMapper<Review> {
}
