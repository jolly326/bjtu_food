package com.bjtufood.auth.service;

import com.bjtufood.auth.dto.MfaSetupVO;

import java.util.List;

/**
 * 管理端 MFA（TOTP 第二因子）服务。
 *
 * <p><b>职责边界</b>：本服务只管「第二因子本身」—— 生成待绑定密钥、确认绑定并发恢复码、
 * 校验一次性口令、停用绑定。登录编排（何时要求第二因子、失败如何计数）留在
 * {@code AdminAuthController}，避免把登录流程的先后次序下沉到本服务。
 *
 * @see com.bjtufood.auth.support.TotpGenerator RFC 6238 实现
 * @see com.bjtufood.auth.support.RecoveryCodes 恢复码
 */
public interface AdminMfaService {

    /**
     * 生成一组「待绑定」凭据（尚未写入账号）。
     *
     * <p>密钥在<b>确认绑定</b>时才落库：用户可能中途扫码失败 / 放弃，
     * 先落库会把账号置于「已要求第二因子但用户还没配好」的死锁状态（自己进不去）。
     *
     * @param username 账号标识（写入 otpauth URI，供认证器展示）
     * @return 密钥与 otpauth URI
     */
    MfaSetupVO createSetup(String username);

    /**
     * 确认绑定：校验用户输入的动态口令后落库密钥，并发下一批一次性恢复码。
     *
     * @param accountId 账号 ID
     * @param secret    待绑定密钥（{@link #createSetup} 下发的那一份）
     * @param code      认证器当前口令
     * @return 明文恢复码（**仅此一次下发**，服务端只留哈希）
     */
    List<String> confirmSetup(Long accountId, String secret, String code);

    /**
     * 校验第二因子：先按 TOTP 校验，未命中则按恢复码校验（设备丢失时的兜底）。
     *
     * @param accountId  账号 ID
     * @param totpSecret 账号已绑定的 TOTP 密钥
     * @param code       用户输入的动态口令或恢复码
     * @return 是否通过（通过时已完成防重放推进 / 恢复码作废）
     */
    boolean verifySecondFactor(Long accountId, String totpSecret, String code);

    /**
     * 停用 MFA：解绑密钥并清空恢复码。
     *
     * @param accountId 账号 ID
     */
    void disable(Long accountId);
}
