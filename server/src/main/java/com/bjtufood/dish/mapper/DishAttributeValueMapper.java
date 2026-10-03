package com.bjtufood.dish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.dish.entity.DishAttributeValue;

/**
 * 菜品描述属性取值 Mapper（取值字典，A4）。
 * <p>
 * 基础 CRUD 由 {@link BaseMapper} 提供；「被引用不可删」的 `dishCount` 由 Service 经
 * {@code DishMapper} 统计（读 `dish.attributes` JSON 内是否出现该 ID）。
 */
public interface DishAttributeValueMapper extends BaseMapper<DishAttributeValue> {
}
