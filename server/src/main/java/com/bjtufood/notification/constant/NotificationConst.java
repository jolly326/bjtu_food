package com.bjtufood.notification.constant;

/**
 * 消息通知相关常量
 */
public interface NotificationConst {

    /** 反馈处理结果回执（管理员标记处理后向可归属提交人投递） */
    String TYPE_FEEDBACK_HANDLE = "feedback_handle";

    /** 菜品信息纠错处理回执（采纳/拒绝后向可归属提交人投递） */
    String TYPE_CORRECTION_HANDLE = "correction_handle";

    /** 评价隐藏回执（管理员隐藏评价后向作者投递；隐藏后作者本人亦不可见，回执是唯一解释渠道） */
    String TYPE_REVIEW_HIDDEN = "review_hidden";

    /** 评价删除回执（管理员删除评价后向作者投递） */
    String TYPE_REVIEW_DELETED = "review_deleted";
}
