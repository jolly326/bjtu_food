package com.bjtufood.auth.service;

import com.bjtufood.auth.dto.LoginReq;
import com.bjtufood.auth.dto.LoginResp;
import com.bjtufood.auth.dto.PasswordUpdateReq;
import com.bjtufood.auth.dto.ProfileUpdateReq;
import com.bjtufood.auth.dto.RegisterReq;
import com.bjtufood.auth.dto.UserStatsVO;

import java.util.Map;

/**
 * 认证服务接口
 * <p>
 * 处理用户登录、注册等认证相关业务逻辑。
 * 实现类注入 UserMapper 和 JwtUtil。
 */
public interface AuthService {

    /**
     * 发送邮箱验证码
     * <p>
     * 向指定校园邮箱发送 6 位验证码，验证码 BCrypt 加密存入数据库。
     * 同一邮箱同一用途 60 秒内不能重复发送。
     *
     * @param email   校园邮箱（需 @bjtu.edu.cn）
     * @param purpose 用途：login/register
     */
    void createEmailCode(String email, String purpose);

    /**
     * 用户登录（首次登录自动注册）
     * <p>
     * 处理流程：
     * 1. 根据 username 查询用户
     * 2. 校验邮箱验证码
     * 3. 根据邮箱查询已注册用户
     * 4. 检查用户状态（disabled 则拒绝登录）
     * 5. 生成 JWT Token
     * 6. 返回 LoginResp（含 token、用户信息）
     *
     * @param req 登录请求（邮箱 + 验证码）
     * @return 登录响应（token + 用户信息）
     * @throws com.bjtufood.common.exception.BusinessException 密码错误/账号禁用
     */
    LoginResp login(LoginReq req);

    /**
     * 用户注册
     * <p>
     * 处理流程：
     * 1. 检查用户名是否已存在
     * 2. 校验校园邮箱验证码
     * 3. 创建用户（默认角色 user，状态 active）
     * 4. 生成 JWT Token 并返回（注册即登录）
     *
     * @param req 注册请求
     * @return 登录响应（同登录）
     * @throws com.bjtufood.common.exception.BusinessException 用户名已存在
     */
    LoginResp register(RegisterReq req);

    /**
     * 获取当前用户个人信息
     *
     * @param userId 用户ID
     * @return 用户信息 Map（id, nickname, avatar, role）
     */
    Map<String, Object> getProfile(Long userId);

    /**
     * 修改个人信息
     *
     * @param userId 用户ID
     * @param req    修改内容（nickname / avatar）
     * @return 更新后的用户信息 Map（id, nickname, avatar, role）
     */
    Map<String, Object> updateProfile(Long userId, ProfileUpdateReq req);

    /**
     * 获取用户统计
     *
     * @param userId 用户ID
     * @return 用户统计数据（favoriteCount, reviewCount）
     */
    UserStatsVO getUserStats(Long userId);

    /**
     * 修改密码
     * <p>
     * 校验旧密码后使用 BCrypt 加密新密码保存。
     *
     * @param userId 用户ID
     * @param req    旧密码和新密码
     * @throws com.bjtufood.common.exception.BusinessException 旧密码错误/用户不存在
     */
    void updatePassword(Long userId, PasswordUpdateReq req);
}
