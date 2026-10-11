package com.bjtufood.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.feedback.entity.FeedbackOption;

/**
 * 反馈类型字典 Mapper。
 * <p>
 * 基础 CRUD 由 {@link BaseMapper} 提供；引用计数（{@code usageCount}）由 Service 经
 * {@code FeedbackMapper} 统计 {@code feedback.option_id = id}（**同域内跨表**）。
 */
public interface FeedbackOptionMapper extends BaseMapper<FeedbackOption> {
}
