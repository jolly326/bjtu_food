package com.bjtufood.feedback.constant;

import java.util.Set;

/**
 * 用户反馈相关常量（类型/状态值域单一真源）。
 * <p>
 * 架构收口 P0-2：自 {@code common.constant} 迁至 {@code feedback.constant}——
 * 业务字面值不属于 common（common 只放跨模块通用件），避免 common 成为「业务常量垃圾场」。
 */
public interface FeedbackConst {

    /** 反馈类型：我要反馈问题（textarea 内容 + 配图） */
    String TYPE_ISSUE = "issue";
    /** 反馈类型：功能建议 / 内容纠错 / 系统问题 / 其他 / 举报（suggestion/add/error 为历史遗留类型、禁新增） */
    String TYPE_SUGGESTION = "suggestion";
    String TYPE_ERROR = "error";
    /** 新增菜品（历史遗留类型、禁新增） */
    String TYPE_ADD = "add";
    /** 系统问题（bug：加载失败/闪退/数据异常等）。<b>历史遗留类型、禁新增</b>：端上已无生产者（P3-10） */
    String TYPE_BUG = "bug";
    /** 其他。<b>历史遗留类型、禁新增</b>：端上已无生产者（P3-10） */
    String TYPE_OTHER = "other";
    String TYPE_REPORT = "report";

    /**
     * 反馈类型写入白名单（单一真源）：{@code POST /feedback} 仅接受三类纯反馈。
     * <p>
     * 方案 B（写入口拆分）后：<b>意见反馈</b>仅 {@code bug}（小程序功能 Bug）/
     * {@code suggestion}（产品功能建议）/ {@code other}（其他平台相关问题）可写；
     * <b>举报</b>改走独立端点 {@code POST /reviews/{id}/report}（不再经本端点写 {@code report}）；
     * {@code error}（菜品信息纠错）早前已迁出为 {@code POST /dishes/{id}/correction}；
     * {@code issue} / {@code add} 为历史遗留写值，<b>均禁新增</b>（历史数据仍可读、可筛选，见 {@link #QUERY_TYPES}）。
     */
    Set<String> WRITABLE_TYPES = Set.of(
            TYPE_BUG, TYPE_SUGGESTION, TYPE_OTHER);

    /**
     * 反馈类型查询白名单（含全部历史类型，供后台筛选存量数据，P2-01 兼容要求）。
     * <p>
     * 比 {@link #WRITABLE_TYPES} 多出历史遗留类型（suggestion/add/error/bug/other），
     * 保证后台按历史类型筛选仍能查到老数据（不因收紧写入口而让老数据显示异常）。
     */
    Set<String> QUERY_TYPES = Set.of(
            TYPE_ISSUE, TYPE_REPORT,
            TYPE_SUGGESTION, TYPE_ADD, TYPE_ERROR, TYPE_BUG, TYPE_OTHER);

    // 反馈二级分类 sub（DEV-01 补全落库）：写入侧仅 `type=report`（举报原因，取值来自 report_reason 表）消费 sub；
    // `type=suggestion`（提个想法，历史遗留、禁新增）存量数据的 idea/problem 值仍可读、可筛选。
    // type 与 sub 不匹配时：未提供（null/空白）按未填处理、落库 NULL；一旦提供（非空白）即 400
    // （严格模式，用户拍板，不静默忽略），避免跨类型污染。

    // ==================== 举报原因 ====================
    // 举报原因字典**已改为表驱动**（`report_reason` 表，A7 落地 2026-10-03）：
    // 口径真源见 docs/schema/report_reason.md 与 docs/api/web/report-reasons.md；
    // 本类**不持有**举报原因常量（值域由 `report_reason` 表下发）。
    // 读取与校验入口：{@code ReportReasonService#listEnabled()}（公开下发）与 {@code isSubmittable()}（提交白名单）。
    //
    // ⚠️ 测试中若需具体机器值，直接用字面量（如 "spam"）或读表，**不要再引入本类常量**。


    /** 举报关联类型（举报对象：菜品评价） */
    String RELATED_REVIEW = "review";

    /**
     * 信息纠错关联类型（关联对象：菜品，related_id = dish.id）——与 schema user_feedback.related_type 注释一致。
     * 管理端列表按本值判定是否补全 relatedDishName（DEV-04）。
     */
    String RELATED_DISH = "dish";

    /** 处理状态：待处理 / 已处理 */
    String STATUS_PENDING = "pending";
    String STATUS_HANDLED = "handled";

    /** 处理状态查询白名单（后台筛选入参校验用） */
    Set<String> QUERY_STATUSES = Set.of(STATUS_PENDING, STATUS_HANDLED);

    /**
     * 板块查询白名单（`GET /admin/feedbacks?category=`）：
     * <ul>
     *   <li>{@code feedback} = 意见反馈（B2）→ <b>排除</b> {@code type='report'}</li>
     *   <li>{@code report} = 举报管理（B3）→ <b>仅</b> {@code type='report'}</li>
     * </ul>
     * 不传 = 全部（兼容不区分板块的调用）。口径见
     * docs/api/web/feedback.md。
     */
    Set<String> QUERY_CATEGORIES = Set.of("feedback", "report");

    /**
     * 处理结论：{@code handled}=通过/已处理（缺省值）；
     * {@code rejected}=不采纳/退回（此时 reject_reason 必填，1~200 字）。
     */
    String OUTCOME_HANDLED = "handled";
    String OUTCOME_REJECTED = "rejected";

    /** 处理结论白名单（后台处理入参校验用；未传按 handled 缺省） */
    Set<String> OUTCOMES = Set.of(OUTCOME_HANDLED, OUTCOME_REJECTED);

    /** 不采纳原因最大长度（schema user_feedback.reject_reason VARCHAR(200)） */
    int REJECT_REASON_MAX_LENGTH = 200;

    /**
     * 处理回复最大长度（≤600 字，口径见 docs/api/web/feedback.md）。
     * <p>
     * 回执正文 = 固定前缀（≤40 字）+ 本回复全文 ⇒ 构造后必 ≤ 1024（`notification.content` 列宽），
     * 故**不得**放宽本上限而不改列宽。
     */
    int REPLY_MAX_LENGTH = 600;
}
