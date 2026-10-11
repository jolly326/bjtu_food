package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.entity.FeedbackOption;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.feedback.mapper.FeedbackOptionMapper;
import com.bjtufood.feedback.service.FeedbackOptionService;
import org.springframework.stereotype.Service;

/**
 * 反馈类型字典服务实现（`feedback_option`）。
 * <p>
 * 规则（启停上下限 / 引用计数 / 改名免费）继承自 {@link AbstractOptionDictionaryService}，
 * 本类只提供两处差异：自己的表与自己的消费表（反馈表按 `option_id` 统计引用）。
 */
@Service
public class FeedbackOptionServiceImpl extends AbstractOptionDictionaryService<FeedbackOption>
        implements FeedbackOptionService {

    private final FeedbackMapper feedbackMapper;

    public FeedbackOptionServiceImpl(FeedbackOptionMapper feedbackOptionMapper, FeedbackMapper feedbackMapper) {
        super(feedbackOptionMapper, FeedbackOption::new, "反馈");
        this.feedbackMapper = feedbackMapper;
    }

    @Override
    protected long countUsage(Long optionId) {
        return feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getOptionId, optionId));
    }
}
