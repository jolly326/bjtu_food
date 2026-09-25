package com.bjtufood.correction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.correction.entity.DishCorrection;

/**
 * 菜品信息纠错 Mapper
 * <p>
 * 基础 CRUD 由 MyBatis-Plus 自动生成；管理端列表的联表补齐（dishName/userNickname）
 * 由 Service 层按 id 批量回查（与 FeedbackServiceImpl DEV-04 同口径，消除 N+1），无需自定义 XML。
 */
public interface DishCorrectionMapper extends BaseMapper<DishCorrection> {
}
