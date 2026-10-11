package com.bjtufood.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.feedback.entity.ReportOption;

/**
 * 举报原因字典 Mapper。
 * <p>
 * 基础 CRUD 由 {@link BaseMapper} 提供；引用计数（{@code usageCount}）由 Service 经
 * {@code ReportMapper} 统计 {@code report.option_id = id}（**同域内跨表**）。
 */
public interface ReportOptionMapper extends BaseMapper<ReportOption> {
}
