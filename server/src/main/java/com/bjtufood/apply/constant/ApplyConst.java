package com.bjtufood.apply.constant;

/**
 * 实体贡献统一申请相关常量（task-12.1）
 */
public interface ApplyConst {

    /** 实体类型 */
    String ENTITY_DISH = "DISH";
    String ENTITY_STALL = "STALL";
    String ENTITY_CANTEEN = "CANTEEN";

    /** 申请类型 */
    String TYPE_NEW = "NEW";
    String TYPE_CLOSE = "CLOSE";
    String TYPE_CHANGE = "CHANGE";

    /** 审核状态 */
    String STATUS_PENDING = "pending";
    String STATUS_APPROVED = "approved";
    String STATUS_REJECTED = "rejected";
}
