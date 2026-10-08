package com.bjtufood.auth.service;

import com.bjtufood.auth.entity.AdminAccount;

/**
 * 管理员账号服务（TD-19）。
 *
 * <p><b>为何需要这一层</b>：{@code ArchTests#controllers_mustNotAccessMappers} 禁止 Controller 直连 Mapper
 * （直连会绕过 Service 的业务口径与事务边界，P0-1 分层要求）。登录的「查账号 + 校验状态 + 刷新登录时间」
 * 属业务编排，故收敛于此。
 */
public interface AdminAccountService {

    /**
     * 按登录名查账号（**不存在返回 {@code null}**，由调用方决定如何提示）。
     *
     * @param username 登录名
     * @return 账号实体；不存在为 {@code null}
     */
    AdminAccount findByUsername(String username);

    /**
     * 按 ID 查账号（{@code GET /admin/auth/me} 读取当前登录态用）。
     *
     * @param id 账号 ID
     * @return 账号实体；不存在为 {@code null}
     */
    AdminAccount findById(Long id);

    /**
     * 登录成功后刷新「最近登录时间」。
     *
     * @param accountId 账号 ID
     */
    void touchLastLogin(Long accountId);

    /**
     * 绑定 / 重绑 TOTP 密钥（MFA 启用）。
     *
     * @param accountId 账号 ID
     * @param secret    Base32 密钥
     */
    void bindTotpSecret(Long accountId, String secret);

    /**
     * 解绑 TOTP 密钥（MFA 停用）。
     *
     * @param accountId 账号 ID
     */
    void clearTotpSecret(Long accountId);

    /**
     * 改密：写新哈希 + 改密时刻，并自增凭证版本（既有 token 全部立即失效）。
     *
     * @param accountId    账号 ID
     * @param passwordHash 新口令的 BCrypt 哈希
     */
    void updatePassword(Long accountId, String passwordHash);
}