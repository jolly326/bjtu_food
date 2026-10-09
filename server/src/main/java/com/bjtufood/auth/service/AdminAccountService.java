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

    /** 改密：写新哈希、记录改密时刻并自增凭证版本（既有 token 因此全部失效）。 */
    void updatePassword(Long accountId, String passwordHash);
}