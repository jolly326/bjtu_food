package com.bjtufood.dish.constant;

/**
 * 菜品模块常量（统一审核/上架状态字面量，避免散落字符串）
 */
public interface DishConst {

    /** 上架状态：on=在售 / off=下架 */
    String STATUS_ON = "on";
    String STATUS_OFF = "off";

    /** 审核状态：待审核 / 已通过 / 已退回 */
    String AUDIT_PENDING = "pending";
    String AUDIT_APPROVED = "approved";
    String AUDIT_REJECTED = "rejected";
}
