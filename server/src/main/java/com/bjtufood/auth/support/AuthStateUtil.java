package com.bjtufood.auth.support;

import org.springframework.util.StringUtils;

/**
 * 认证状态判据（**唯一真源**，用户拍板 A 方案收敛）。
 * <p>
 * 语义：{@code user.email} 非空即「已认证」（可写 UGC）；为 NULL 即「游客态」。
 * <p>
 * 为何不用布尔列判据：布尔列与 {@code user.email} 表达同一事实（写入路径恒成对写：
 * 认证 / 释放绑定替换 / 注销 三处同批更新同一行），属同源冗余，user 表不设布尔列。
 * 判据收敛到本类一处，避免各调用点各写一份 {@code != null} 判断而分裂
 * （4031 分流共 3 处调用：{@code RequireVerifiedAspect}、{@code ReviewServiceImpl}、
 * {@code FeedbackServiceImpl}）。
 */
public final class AuthStateUtil {

    private AuthStateUtil() {
    }

    /**
     * 是否已完成学号邮箱认证。
     *
     * @param email 用户绑定邮箱（{@code user.email} 的列值，可空；非空即已认证）
     * @return true=已认证（可写 UGC）/ false=游客态
     */
    public static boolean isVerified(String email) {
        return StringUtils.hasText(email);
    }
}
