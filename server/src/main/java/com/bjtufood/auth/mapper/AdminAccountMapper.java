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
}