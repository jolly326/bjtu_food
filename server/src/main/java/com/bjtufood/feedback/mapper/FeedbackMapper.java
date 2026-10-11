package com.bjtufood.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.feedback.entity.Feedback;

/**
 * 意见反馈 Mapper 接口。
 * <p>
 * 基础 CRUD 由 {@link BaseMapper} 提供；管理端列表的「提交人昵称」与「处置结果」分别经
 * auth 域只读契约与 {@code ModerationTaskService} 批量补齐（消除 N+1），无需自定义 XML。
 */
public interface FeedbackMapper extends BaseMapper<Feedback> {
}
