package com.bjtufood.content.constant;

/**
 * 内容审核相关常量（audit 模块，与 moment 的 MomentConst.AUDIT_* 语义一致）。
 * 全局清扫：消除 AuditServiceImpl 中硬编码的 "approved"/"rejected" 魔法字符串。
 */
public interface AuditConst {

    /** 审核状态：已通过 */
    String STATUS_APPROVED = "approved";

    /** 审核状态：已退回 */
    String STATUS_REJECTED = "rejected";

    /** 审核对象类型：菜品 / 档口 / 食堂 / 动态 */
    String TYPE_DISH = "dish";
    String TYPE_STALL = "stall";
    String TYPE_CANTEEN = "canteen";
    String TYPE_MOMENT = "moment";
}
