package com.bjtufood.common.constant;

/**
 * 用户反馈相关常量
 */
public interface FeedbackConst {

    /** 反馈类型：功能建议 / 内容纠错 / 其他 / 举报 */
    String TYPE_SUGGESTION = "suggestion";
    String TYPE_ERROR = "error";
    String TYPE_OTHER = "other";
    String TYPE_REPORT = "report";

    /** 举报关联类型 */
    String RELATED_MOMENT = "moment";

    /** 处理状态：待处理 / 已处理 */
    String STATUS_PENDING = "pending";
    String STATUS_HANDLED = "handled";
}
