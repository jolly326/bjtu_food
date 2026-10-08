package com.bjtufood.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员账号（表 {@code admin_account}）。
 *
 * <p><b>与学生账号完全分离</b>：{@code user} 表是学生专用（无 {@code role} 列），
 * 管理端**不复用** —— 两类身份的生命周期、鉴权方式、失效条件都不同，混在一张表会让
 * 「哪些字段管学生、哪些管管理员」失去边界。
 *
 * @see com.bjtufood.auth.controller.AdminAuthController 账密登录
 * @see com.bjtufood.auth.config.AdminAuthFilter 管理端 JWT 鉴权
 */
@Data
@TableName("admin_account")
public class AdminAccount {

    /** 账号 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名（唯一） */
    private String username;

    /**
     * 口令哈希（**BCrypt**，不可逆）。
     * <p>
     * 🔴 **明文口令永不落库**：写入前即经 {@code BCryptPasswordEncoder} 编码；
     * 比对时用 {@code matches(raw, hash)}，不可反推。
     */
    private String passwordHash;

    /** 状态：{@code on} 启用 / {@code off} 停用（停用即拒绝登录） */
    private String status;

    /**
     * 角色：{@code super} 全权 / {@code operator} 读写（**不含删除**）/ {@code viewer} 只读。
     * <p>
     * 🔴 <b>门控在服务端强制</b>（{@code AdminAuthFilter} 按「HTTP 方法 + 角色」判定）：
     * 前端按角色隐显入口只是体验，绕过前端直接调接口同样被拦。
     */
    private String role;

    /** 最近登录成功时间（登录时更新，可用于「谁在用」判断） */
    private LocalDateTime lastLoginAt;

    /**
     * TOTP 密钥（Base32）。
     * <p>
     * 🔴 {@code null} = **未绑定 MFA**（登录只走账密一步）；非空 = 已绑定，
     * 账密校验通过后还必须通过第二因子才签发 token。
     * <p>
     * 该值等同于第二因子本身，<b>不出现在任何 VO / 日志</b>；仅在绑定流程中
     * 以 otpauth URI 形态下发一次（供认证器扫码）。
     */
    private String totpSecret;

    /**
     * 已用过的最后一个 TOTP 时间步（防重放）。
     * <p>
     * 同一时间步内认证器产出的口令是同一个 —— 截获到「刚用过」的口令即可在窗口内重放。
     * 故校验通过后以「条件自增」把它推进到当前步，下一步校验要求命中步严格大于它。
     */
    private Long totpLastStep;

    /**
     * 凭证版本：token 内嵌该值的快照，{@code AdminAuthFilter} 逐请求与库中现值比对。
     * <p>
     * 🔴 <b>改密即自增</b> ⇒ 所有既有 token 立即失效（含被盗用的那份），
     * 无需另建吊销表 —— 复用「逐请求回查账号状态」这条既有路径。
     */
    private Integer credentialVersion;

    /** 最近一次改密时刻；{@code null} = 建号后从未改密（超期提示按建号时间起算） */
    private LocalDateTime passwordChangedAt;

    /** 建号时间（{@code password_changed_at} 为空时，口令超期以本字段起算） */
    private LocalDateTime createdAt;

    /** 账号是否启用 */
    public boolean isActive() {
        return "on".equals(status);
    }

    /** 是否已绑定 MFA（第二因子） */
    public boolean isMfaEnabled() {
        return totpSecret != null && !totpSecret.isBlank();
    }
}