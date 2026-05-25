package com.bjtufood.auth.service;

import com.bjtufood.auth.dto.LoginReq;
import com.bjtufood.auth.dto.LoginResp;
import com.bjtufood.auth.dto.RegisterReq;

/**
 * 认证服务接口
 * <p>
 * 处理用户登录、注册等认证相关业务逻辑。
 * 实现类注入 UserMapper 和 JwtUtil。
 */
public interface AuthService {

    /**
     * 用户登录
     * <p>
     * 处理流程：
     * 1. 根据 username 查询用户
     * 2. 校验密码（bcrypt）
     * 3. 检查用户状态（disabled 则拒绝登录）
     * 4. 生成 JWT Token
     * 5. 返回 LoginResp（含 token、userInfo、stallId）
     *
     * @param req 登录请求（用户名 + 密码）
     * @return 登录响应（token + 用户信息）
     * @throws com.bjtufood.common.exception.BusinessException 用户不存在/密码错误/账号禁用
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
}
