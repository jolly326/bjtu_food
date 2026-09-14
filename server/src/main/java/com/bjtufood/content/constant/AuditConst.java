package com.bjtufood.content.constant;

import com.bjtufood.common.constant.AuditStatusConst;

/**
 * 内容审核相关常量（audit 模块）。
 * 全局清扫：消除 AuditServiceImpl 中硬编码的 "approved"/"rejected" 魔法字符串。
 * <p>
 * 审核状态值域真源在 {@link AuditStatusConst}（common/constant），此处仅做别名引用。
 */
public interface AuditConst {

    /** 审核状态：已通过（真源：{@link AuditStatusConst#APPROVED}） */
    String STATUS_APPROVED = AuditStatusConst.APPROVED;

    /** 审核状态：已退回（真源：{@link AuditStatusConst#REJECTED}） */
    String STATUS_REJECTED = AuditStatusConst.REJECTED;

    /** 审核对象类型：菜品 / 档口 / 食堂 */
    String TYPE_DISH = "dish";
    String TYPE_STALL = "stall";
    String TYPE_CANTEEN = "canteen";
}
