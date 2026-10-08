package com.bjtufood.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端 MFA 恢复码（表 {@code admin_recovery_code}）。
 *
 * <p><b>只存哈希</b>：明文恢复码仅在绑定成功时下发一次，库内保存的是 SHA-256 摘要 ——
 * 库被读走也拿不到可用码。{@code used_at} 非空即已失效（一次性，不可复用）。
 *
 * @see com.bjtufood.auth.support.RecoveryCodes 生成与等时比对
 */
@Data
@TableName("admin_recovery_code")
public class AdminRecoveryCode {

    /** 自增 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属管理员（{@code admin_account.id}） */
    private Long adminId;

    /** 恢复码摘要（SHA-256 十六进制；**不存明文**） */
    private String codeHash;

    /** 使用时刻；非空 = 已失效（一次性） */
    private LocalDateTime usedAt;

    /** 生成时间 */
    private LocalDateTime createdAt;
}
