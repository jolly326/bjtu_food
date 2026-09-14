package com.bjtufood.common.constant;

/**
 * 审核状态值域 —— 单一真源（RB15 收敛）。
 * <p>
 * 值域：{@code pending}（待审核）/ {@code approved}（已通过）/ {@code rejected}（已退回）。
 * <p>
 * {@code dish/constant/DishConst} 中的审核状态常量引用本接口，避免同一值域多处定义、改一处漏一处。
 * （原 {@code content/constant/AuditConst} 已随 /admin/audit 死代码清理一并删除）
 * 注意：本包（common/constant）不依赖任何业务包，故不存在跨包循环依赖。
 */
public interface AuditStatusConst {

    /** 审核状态：待审核 */
    String PENDING = "pending";

    /** 审核状态：已通过 */
    String APPROVED = "approved";

    /** 审核状态：已退回 */
    String REJECTED = "rejected";

    /** 全部合法值域（查询入参白名单校验用，单一真源） */
    java.util.Set<String> ALL = java.util.Set.of(PENDING, APPROVED, REJECTED);
}
