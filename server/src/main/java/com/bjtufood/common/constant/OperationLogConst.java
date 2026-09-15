package com.bjtufood.common.constant;

/**
 * 操作日志相关常量（动作标识）
 */
public interface OperationLogConst {

    String ACTION_REVIEW_HIDE = "review_hide";
    String ACTION_REVIEW_DELETE = "review_delete";
    // ACTION_REVIEW_SEC_STATE（"review_sec_state"）已随 sec_state 全链退役删除（2026-09-15 取消人工复核）
    String ACTION_DISH_DELETE = "dish_delete";
    String ACTION_FEEDBACK_HANDLE = "feedback_handle";
    String ACTION_ACCOUNT_DELETE = "account_delete";
}
