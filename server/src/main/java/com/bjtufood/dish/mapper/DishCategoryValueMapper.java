package com.bjtufood.dish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.dish.entity.DishCategoryValue;

/**
 * 菜品分类值字典 Mapper（A6）。
 * <p>
 * 基础 CRUD 由 {@link BaseMapper} 提供；`dishCount`（被引用菜品数）与「合并」时的
 * `dish.meal_type` 批量改写由 Service 经 {@code DishMapper} 完成。
 */
public interface DishCategoryValueMapper extends BaseMapper<DishCategoryValue> {
}
