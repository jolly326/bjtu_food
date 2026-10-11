package com.bjtufood.correction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.correction.entity.Correction;

/**
 * 菜品问题反馈 Mapper（表 `correction`）。
 * <p>
 * 基础 CRUD 由 MyBatis-Plus 自动生成；管理端列表的联表补齐（dishName / userName）
 * 由 Service 层按 id 批量回查（与 FeedbackServiceImpl 同口径，消除 N+1），
 * 处置结果则由 {@code ModerationTaskService} 按 {@code type_id} 集合批量回填，无需自定义 XML。
 */
public interface CorrectionMapper extends BaseMapper<Correction> {
}
