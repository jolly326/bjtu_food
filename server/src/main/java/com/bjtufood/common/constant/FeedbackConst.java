package com.bjtufood.common.constant;

/**
 * 用户反馈相关常量
 */
public interface FeedbackConst {

    /** 反馈类型：功能建议 / 内容纠错 / 系统问题 / 其他 / 举报 */
    String TYPE_SUGGESTION = "suggestion";
    String TYPE_ERROR = "error";
    /** 新增菜品（2026-08-17：从「内容纠错」二级细分提升为一级类型，承载 UGC 补录） */
    String TYPE_ADD = "add";
    /** 系统问题（bug：加载失败/闪退/数据异常等，与功能建议分开，便于 admin 筛选与优先级处理） */
    String TYPE_BUG = "bug";
    String TYPE_OTHER = "other";
    String TYPE_REPORT = "report";

    /** 举报关联类型 */
    String RELATED_MOMENT = "moment";
    String RELATED_MOMENT_COMMENT = "moment_comment";

    /** 处理状态：待处理 / 已处理 */
    String STATUS_PENDING = "pending";
    String STATUS_HANDLED = "handled";
}
