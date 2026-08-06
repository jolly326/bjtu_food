package com.bjtufood.moment.constant;

/**
 * 社区动态相关常量
 */
public interface MomentConst {

    /** 审核状态：待审核 / 已通过 / 已退回 */
    String AUDIT_PENDING = "pending";
    String AUDIT_APPROVED = "approved";
    String AUDIT_REJECTED = "rejected";

    /** 关联对象类型：菜品 / 档口 / 无 */
    String RELATED_DISH = "dish";
    String RELATED_STALL = "stall";
    String RELATED_NONE = "none";

    /** 下架状态：0=正常 1=管理员强制下架 */
    int STATUS_NORMAL = 0;
    int STATUS_HIDDEN = 1;
}
