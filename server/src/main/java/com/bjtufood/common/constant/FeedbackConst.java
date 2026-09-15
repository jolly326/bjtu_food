package com.bjtufood.common.constant;

import java.util.Set;

/**
 * 用户反馈相关常量（类型/状态值域单一真源）。
 */
public interface FeedbackConst {

    /** 反馈类型：功能建议 / 内容纠错 / 系统问题 / 其他 / 举报 */
    String TYPE_SUGGESTION = "suggestion";
    String TYPE_ERROR = "error";
    /** 新增菜品（2026-08-17：从「内容纠错」二级细分提升为一级类型，承载 UGC 补录） */
    String TYPE_ADD = "add";
    /** 系统问题（bug：加载失败/闪退/数据异常等）。<b>历史遗留类型、禁新增</b>：端上已无生产者（P3-10） */
    String TYPE_BUG = "bug";
    /** 其他。<b>历史遗留类型、禁新增</b>：端上已无生产者（P3-10） */
    String TYPE_OTHER = "other";
    String TYPE_REPORT = "report";

    /**
     * 反馈类型写入白名单（单一真源，P3-10 收敛）。
     * <p>
     * 仅端上真实产出的 4 类：{@code suggestion}（提个想法页）/ {@code add}（推荐菜品页）/
     * {@code error}（信息不对页）/ {@code report}（菜品详情页举报）。
     * {@code bug} / {@code other} 已下线、无任何生产者，禁止新增写入（历史数据仍可读、可筛选）。
     */
    Set<String> WRITABLE_TYPES = Set.of(TYPE_SUGGESTION, TYPE_ADD, TYPE_ERROR, TYPE_REPORT);

    /**
     * 反馈类型查询白名单（含全部历史类型，供后台筛选存量数据，P2-01 兼容要求）。
     * <p>
     * 比 {@link #WRITABLE_TYPES} 多出历史遗留的 {@code bug} / {@code other}，
     * 保证后台按历史类型筛选仍能查到老数据（不因收紧写入口而让老数据显示异常）。
     */
    Set<String> QUERY_TYPES = Set.of(TYPE_SUGGESTION, TYPE_ADD, TYPE_ERROR, TYPE_BUG, TYPE_OTHER, TYPE_REPORT);

    /**
     * 反馈二级分类（sub，DEV-01 补全落库）：<b>仅</b> {@code type=suggestion}（提个想法）有效。
     * <p>
     * 端上「提个想法」页存在「想法 / 问题」二选一，此前该值仅在请求中出现、未落库（假字段）；
     * 现收敛为服务端白名单并落库 {@code user_feedback.sub}，供管理端按二级分类查看。
     * 其他 type 一律忽略该值并置 NULL（不报错、不落库），避免跨类型污染。
     */
    String SUB_IDEA = "idea";
    String SUB_PROBLEM = "problem";

    /**
     * 反馈二级分类写入白名单（单一真源）：仅 {@code idea} / {@code problem}。
     * <p>
     * provided 且不在白名单 → 400（PR-06：非法入参必须报错，不静默降级）；
     * 未提供（null/空白）→ 按未填处理（落库 NULL），由 Service 归一化。
     */
    Set<String> SUB_WRITE_WHITELIST = Set.of(SUB_IDEA, SUB_PROBLEM);

    /** 举报关联类型（举报对象：菜品评价） */
    String RELATED_REVIEW = "review";

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
