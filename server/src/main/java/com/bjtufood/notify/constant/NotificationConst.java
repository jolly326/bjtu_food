package com.bjtufood.notify.constant;

/**
 * 消息通知相关常量
 */
public interface NotificationConst {

    /** 反馈处理结果回执（管理员标记处理后向可归属提交人投递） */
    String TYPE_FEEDBACK_HANDLE = "feedback_handle";

    /** 菜品信息纠错处理回执（采纳/拒绝后向可归属提交人投递） */
    String TYPE_CORRECTION_HANDLE = "correction_handle";
}
