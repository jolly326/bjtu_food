package com.bjtufood.dish.constant;

import com.bjtufood.common.constant.AuditStatusConst;

/**
 * 菜品模块常量（统一上架/审核状态字面量，避免散落字符串）
 * <p>
 * 审核状态值域真源在 {@link AuditStatusConst}（common/constant），此处仅做别名引用（值域：pending/approved/rejected）。
 */
public interface DishConst {

    /** 上架状态：on=在售 / off=下架 */
    String STATUS_ON = "on";
    String STATUS_OFF = "off";

    /** 审核状态：待审核（真源：{@link AuditStatusConst#PENDING}） */
    String AUDIT_PENDING = AuditStatusConst.PENDING;

    /** 审核状态：已通过（真源：{@link AuditStatusConst#APPROVED}） */
    String AUDIT_APPROVED = AuditStatusConst.APPROVED;

    /** 审核状态：已退回（真源：{@link AuditStatusConst#REJECTED}） */
    String AUDIT_REJECTED = AuditStatusConst.REJECTED;
}
