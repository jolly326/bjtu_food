package com.bjtufood.auth.service;

import com.bjtufood.auth.dto.LoginReq;
import com.bjtufood.auth.dto.LoginResp;
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
     * 用户登录（首次登录自动注册）
     * <p>
     * 处理流程：
     * 1. 根据 username 查询用户
     * 2. 用户不存在 → 自动创建 student 用户
     * 3. 用户存在 → 校验密码（bcrypt）
     * 4. 检查用户状态（disabled 则拒绝登录）
     * 5. 生成 JWT Token
     * 6. 返回 LoginResp（含 token、userInfo、stallId）
     *
     * @param req 登录请求（用户名 + 密码）
     * @return 登录响应（token + 用户信息）
     * @throws com.bjtufood.common.exception.BusinessException 密码错误/账号禁用
     */
    LoginResp login(LoginReq req);

    /**
     * 用户注册
     * <p>
     * 处理流程：
     * 1. 检查用户名是否已存在
     * 2. 密码 bcrypt 加密
     * 3. 创建用户（默认角色 student，状态 active）
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
}
