package com.bjtufood.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员账号（表 {@code admin_account}）。
 *
 * <p><b>与学生账号完全分离</b>：{@code user} 表是学生专用（{@code role} 列已移除），
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

    /** 最近登录成功时间（登录时更新，可用于「谁在用」判断） */
    private LocalDateTime lastLoginAt;

    /** 账号是否启用 */
    public boolean isActive() {
        return "on".equals(status);
    }
}