package com.bjtufood.notify.constant;

/**
 * 消息通知相关常量
 */
public interface NotificationConst {

    /**
     * 通知类型：菜品审核结果（仅存量兼容）。
     * <p>
     * 2026-09-14 用户拍板（Q-107）：唯一触发点（学生对菜品写接口 → /admin/audit 审核）
     * 已随死代码清理下线，本类型不再产生新通知；保留常量仅供历史数据读取/展示兼容
     * （Notification 实体按 type 原样返回，读取存量 dish_audit 行不报错）。
     */
    String TYPE_DISH_AUDIT = "dish_audit";
    /** 反馈处理结果回执（管理员标记处理后向可归属提交人投递） */
    String TYPE_FEEDBACK_HANDLE = "feedback_handle";
}
