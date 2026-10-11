package com.bjtufood.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.feedback.entity.Report;

/**
 * 评价举报 Mapper 接口。
 * <p>
 * 基础 CRUD 由 {@link BaseMapper} 提供；管理端列表的被举报评价摘要经 review 域只读契约
 * 批量补齐（消除 N+1），处置结果经 {@code ModerationTaskService} 批量补齐，无需自定义 XML。
 */
public interface ReportMapper extends BaseMapper<Report> {
}
