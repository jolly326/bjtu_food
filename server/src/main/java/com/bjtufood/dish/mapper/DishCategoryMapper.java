package com.bjtufood.dish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.dish.entity.DishCategory;

/**
 * 菜品种类字典 Mapper。
 * <p>
 * 基础 CRUD 由 {@link BaseMapper} 提供；「被引用不可删」的 `dishCount` 由 Service 经
 * {@code DishMapper} 统计（数 {@code dish.category_id} = 该种类 ID 的行数）。
 */
public interface DishCategoryMapper extends BaseMapper<DishCategory> {
}
