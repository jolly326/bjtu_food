package com.bjtufood.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.auth.entity.AdminRecoveryCode;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 管理端 MFA 恢复码 Mapper。
 */
@Mapper
public interface AdminRecoveryCodeMapper extends BaseMapper<AdminRecoveryCode> {

    /**
     * 作废单条恢复码（用后作废）。
     *
     * <p>带 {@code used_at IS NULL} 条件：并发提交同一码时只有第一个能作废成功。
     *
     * @param id      恢复码行 ID
     * @param usedAt  使用时刻
     * @return 1 = 作废成功（本次为该码的首次使用）；0 = 已被用过
     */
    @Update("UPDATE admin_recovery_code SET used_at = #{usedAt} WHERE id = #{id} AND used_at IS NULL")
    int markUsed(@Param("id") Long id, @Param("usedAt") LocalDateTime usedAt);

    /**
     * 清空某管理员的全部恢复码（重新绑定 / 停用 MFA 时调用）。
     *
     * @param adminId 管理员账号 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM admin_recovery_code WHERE admin_id = #{adminId}")
    int deleteByAdminId(@Param("adminId") Long adminId);
}
