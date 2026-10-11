package com.bjtufood.feedback.service;

/**
 * 反馈类型字典服务（`feedback_option`）。
 *
 * <p>契约真源：docs/schema/feedback_option.md 与 docs/api/web/feedback-options.md。
 * <p>消费点：意见反馈页的类型下拉（公开 `GET /feedback-options`）与提交白名单
 * （{@code FeedbackService#submit} 校验 `optionId` 命中启用项）。
 */
public interface FeedbackOptionService extends OptionDictionaryService {
}
