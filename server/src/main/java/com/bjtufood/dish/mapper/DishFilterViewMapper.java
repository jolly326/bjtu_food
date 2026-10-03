package com.bjtufood.dish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.dish.entity.DishFilterView;

/**
 * 首页筛选视图 Mapper（A6）。
 * <p>
 * 基础 CRUD 由 {@link BaseMapper} 提供；`matchedCount`（匹配的在售菜品数）由 Service 经
 * {@code DishMapper} 的**条件计数**（白名单 + 参数化）得出。
 */
public interface DishFilterViewMapper extends BaseMapper<DishFilterView> {
}
