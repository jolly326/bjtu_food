package com.bjtufood.feedback.service;

/**
 * 举报原因字典服务（`report_option`）。
 *
 * <p>契约真源：docs/schema/report_option.md 与 docs/api/web/report-options.md。
 * <p>消费点：举报弹层的原因单选（公开 `GET /report-options`）、举报提交白名单
 * （{@code ReportService#submit} 校验 `optionId` 命中启用项）与举报管理端列表的原因翻译。
 */
public interface ReportOptionService extends OptionDictionaryService {
}
