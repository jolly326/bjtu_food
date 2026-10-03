package com.bjtufood.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.feedback.entity.ReportReason;
import org.apache.ibatis.annotations.Mapper;

/**
 * 举报原因字典 Mapper（A7）。
 * <p>
 * 基础 CRUD 由 {@link BaseMapper} 提供；引用计数（`feedbackCount`）由 Service 经
 * {@code FeedbackMapper} 统计 `type='report' AND sub=value`（**跨表但在本域内**）。
 */
@Mapper
public interface ReportReasonMapper extends BaseMapper<ReportReason> {
}
