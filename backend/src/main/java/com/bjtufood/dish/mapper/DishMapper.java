package com.bjtufood.dish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.dish.entity.Dish;

/**
 * 菜品 Mapper 接口
 * <p>
 * 基础 CRUD 由 MyBatis-Plus 自动实现。
 * 复杂查询方法在 DishMapper.xml 中定义（如多表关联查询、动态排序等）。
 */
public interface DishMapper extends BaseMapper<Dish> {
}
