package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.feedback.entity.Report;
import com.bjtufood.feedback.entity.ReportOption;
import com.bjtufood.feedback.mapper.ReportMapper;
import com.bjtufood.feedback.mapper.ReportOptionMapper;
import com.bjtufood.feedback.service.ReportOptionService;
import org.springframework.stereotype.Service;

/**
 * 举报原因字典服务实现（`report_option`）。
 * <p>
 * 规则（启停上下限 / 引用计数 / 改名免费）继承自 {@link AbstractOptionDictionaryService}，
 * 本类只提供两处差异：自己的表与自己的消费表（举报表按 `option_id` 统计引用）。
 */
@Service
public class ReportOptionServiceImpl extends AbstractOptionDictionaryService<ReportOption>
        implements ReportOptionService {

    private final ReportMapper reportMapper;

    public ReportOptionServiceImpl(ReportOptionMapper reportOptionMapper, ReportMapper reportMapper) {
        super(reportOptionMapper, ReportOption::new, "举报");
        this.reportMapper = reportMapper;
    }

    @Override
    protected long countUsage(Long optionId) {
        return reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getOptionId, optionId));
    }
}
