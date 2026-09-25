package com.bjtufood.common.constant;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户反馈相关常量（类型/状态值域单一真源）。
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
     * 反馈类型写入白名单（单一真源）。
     * <p>
     * 仅端上真实产出的 2 类：{@code issue}（我要反馈问题）/ {@code report}（菜品详情页举报）。
     * suggestion/add/error/bug/other 为历史遗留类型、端上已无生产者，禁止新增写入
     * （历史数据仍可读、可筛选）。
     */
    Set<String> WRITABLE_TYPES = Set.of(TYPE_ISSUE, TYPE_REPORT);

    /**
     * 反馈类型查询白名单（含全部历史类型，供后台筛选存量数据，P2-01 兼容要求）。
     * <p>
     * 比 {@link #WRITABLE_TYPES} 多出历史遗留类型（suggestion/add/error/bug/other），
     * 保证后台按历史类型筛选仍能查到老数据（不因收紧写入口而让老数据显示异常）。
     */
    Set<String> QUERY_TYPES = Set.of(
            TYPE_ISSUE, TYPE_REPORT,
            TYPE_SUGGESTION, TYPE_ADD, TYPE_ERROR, TYPE_BUG, TYPE_OTHER);

    /**
     * 反馈二级分类（sub，DEV-01 补全落库）：当前写入侧仅 {@code type=report}（举报原因）消费 sub；
     * {@code type=suggestion}（提个想法，历史遗留、禁新增）存量数据的 idea/problem 值仍可读、可筛选。
     * type 与 sub 不匹配时：未提供（null/空白）按未填处理、落库 NULL；
     * 一旦提供（非空白）即 400（严格模式，2026-09-15 用户拍板，不静默忽略），避免跨类型污染。
     */
    // ==================== 举报原因（type=report 的二级分类，字典下发给端上单选） ====================

    /** 举报原因项（value = 机器值，label = 中文标签；经 {@code GET /feedback/report-reasons} 字典下发） */
    record ReportReason(String value, String label) {}

    String REPORT_SPAM = "spam";
    String REPORT_ABUSE = "abuse";
    String REPORT_PORN = "porn";
    String REPORT_ILLEGAL = "illegal";
    String REPORT_FAKE = "fake";
    String REPORT_OTHER = "other";

    /**
     * 举报原因字典（**唯一真源**，List.of 保序 = 下发展示顺序）：端上单选弹层与管理端原因翻译
     * 均消费 {@code GET /feedback/report-reasons} 下发的同一份，**零硬编码**（PR-12）。
     */
    List<ReportReason> REPORT_REASONS = List.of(
            new ReportReason(REPORT_SPAM, "垃圾广告 / 营销刷屏"),
            new ReportReason(REPORT_ABUSE, "辱骂攻击"),
            new ReportReason(REPORT_PORN, "色情低俗"),
            new ReportReason(REPORT_ILLEGAL, "违法违规"),
            new ReportReason(REPORT_FAKE, "虚假信息 / 虚假评价"),
            new ReportReason(REPORT_OTHER, "其他问题"));

    /** 举报原因写入白名单（report 类型 sub **必选**其一，PR-06：非法 / 缺失即 400） */
    Set<String> REPORT_REASON_VALUES = REPORT_REASONS.stream()
            .map(ReportReason::value).collect(Collectors.toUnmodifiableSet());

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
     * 处理结论（§7.23 第 5 条）：{@code handled}=通过/已处理（缺省值）；
     * {@code rejected}=不采纳/退回（此时 reject_reason 必填，1~200 字）。
     */
    String OUTCOME_HANDLED = "handled";
    String OUTCOME_REJECTED = "rejected";

    /** 处理结论白名单（后台处理入参校验用；未传按 handled 缺省） */
    Set<String> OUTCOMES = Set.of(OUTCOME_HANDLED, OUTCOME_REJECTED);

    /** 不采纳原因最大长度（schema user_feedback.reject_reason VARCHAR(200)，§7.23 第 5 条） */
    int REJECT_REASON_MAX_LENGTH = 200;
}
