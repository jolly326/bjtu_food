package com.bjtufood.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.auth.entity.AdminAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 管理员账号 Mapper。
 *
 * <p><b>为何单独一个 Mapper</b>：{@code admin_account} 与学生 {@code user} 表身份体系完全独立
 * （见 {@link AdminAccount} 类注释），不在任何情况下 join 或互相复用。
 */
@Mapper
public interface AdminAccountMapper extends BaseMapper<AdminAccount> {

    /**
     * 登录成功后刷新「最近登录时间」。
     *
     * <p>与读操作分离成独立 SQL：登录主链路只做一次 {@code UPDATE ... SET last_login_at}，
     * 不必为此重查整行。
     *
     * @param id          账号 ID
     * @param lastLoginAt 登录时刻（应用侧当前时间）
     * @return 影响行数
     */
    @Update("UPDATE admin_account SET last_login_at = #{lastLoginAt} WHERE id = #{id}")
    int touchLastLogin(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    /**
     * 绑定 / 重绑 TOTP 密钥（MFA 启用），并复位防重放时间步。
     *
     * @param id     账号 ID
     * @param secret Base32 密钥
     * @return 影响行数
     */
    @Update("UPDATE admin_account SET totp_secret = #{secret}, totp_last_step = NULL WHERE id = #{id}")
    int updateTotpSecret(@Param("id") Long id, @Param("secret") String secret);

    /**
     * 解绑 TOTP 密钥（MFA 停用，恢复为「仅账密」形态），并复位防重放时间步。
     *
     * @param id 账号 ID
     * @return 影响行数
     */
    @Update("UPDATE admin_account SET totp_secret = NULL, totp_last_step = NULL WHERE id = #{id}")
    int clearTotpSecret(@Param("id") Long id);

    /**
     * 推进防重放时间步（仅在目标步「新于」当前记录时生效）。
     *
     * <p>🔴 <b>条件自增即重放防护</b>：把「判断 + 写入」压成一条原子 SQL，
     * 并发的重放请求里只有第一个能推进成功，其余得到 0 行 ⇒ 直接被拒。
     *
     * @param id  账号 ID
     * @param step 本次命中的时间步
     * @return 1 = 推进成功（本次口令未被用过）；0 = 该步已用过（重放）
     */
    @Update("UPDATE admin_account SET totp_last_step = #{step}"
            + " WHERE id = #{id} AND (totp_last_step IS NULL OR totp_last_step < #{step})")
    int advanceTotpStep(@Param("id") Long id, @Param("step") long step);

    /**
     * 改密：写入新哈希与改密时刻，并<b>自增凭证版本</b>（既有 token 全部立即失效）。
     *
     * @param id           账号 ID
     * @param passwordHash 新口令的 BCrypt 哈希
     * @param changedAt    改密时刻
     * @return 影响行数
     */
    @Update("UPDATE admin_account SET password_hash = #{passwordHash}, password_changed_at = #{changedAt},"
            + " credential_version = credential_version + 1 WHERE id = #{id}")
    int updatePassword(@Param("id") Long id,
                       @Param("passwordHash") String passwordHash,
                       @Param("changedAt") LocalDateTime changedAt);
}