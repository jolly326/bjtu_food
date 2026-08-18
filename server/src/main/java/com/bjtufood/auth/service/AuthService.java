package com.bjtufood.auth.service;

import com.bjtufood.auth.dto.AdminLoginReq;
import com.bjtufood.auth.dto.AdminLoginResp;
import com.bjtufood.auth.dto.LoginResp;
import com.bjtufood.auth.dto.ProfileUpdateReq;
import com.bjtufood.auth.dto.UserStatsVO;
import com.bjtufood.auth.dto.UserInfoVO;
import com.bjtufood.auth.entity.User;

import java.util.Map;

/**
 * 认证服务接口（微信登录体系，spec §5.y）
 * <p>
 * 小程序端无账号密码：微信静默登录（wechat-login）→ 游客态（verified=0）；
 * 邮箱验证码认证（verify-email）解锁社区写操作。管理后台走独立方案 C（/auth/admin/login）。
 */
public interface AuthService {

    /**
     * 发送邮箱验证码（认证用途）。
     * <p>
     * purpose 收窄为 verify（替代旧 login/register/reset，spec §5.y.5）。
     * 同邮箱同用途 60s 限频、验证码 10min 有效，验证码不随响应返回（邮件发送）。
     *
     * @param username 学号（可选）；未传 email 时收件邮箱推导为 {username}@bjtu.edu.cn
     * @param email    校园邮箱（可选，需 @bjtu.edu.cn）；与 username 二选一
     * @param purpose  用途：仅 verify
     */
    void createEmailCode(String username, String email, String purpose);

    /**
     * 微信静默登录（spec §5.y.1 / task-01 1.1）。
     * <p>
     * 后端 code2Session 换 openid（+unionid 若有）→ 按 user.openid 取号：
     * 存在则更新 last_login_at 返回原账号；不存在则自动建号（游客态 verified=0）。
     *
     * @param code 微信 wx.login 临时凭证
     * @return LoginResp{token, userInfo}
     * @throws com.bjtufood.common.exception.BusinessException code2Session 失败时 400
     */
    LoginResp wechatLogin(String code);

    /**
     * 学号邮箱认证（spec §5.y.3 / task-01 1.3）。
     * <p>
     * 校验验证码 → 按邮箱执行数据迁移合并 / 绑定替换 → 置 verified=1、写 bind_email/verified_at → 返回更新后 LoginResp。
     * 邮箱是唯一迁移 / 绑定凭证；不设解绑入口。
     *
     * @param code   邮箱验证码（对应记录推导绑定邮箱）
     * @param userId 当前微信账号 ID（SecurityUtil 取）
     * @return 更新后 LoginResp{token, userInfo}
     */
    LoginResp verifyEmail(String code, Long userId);

    /**
     * 获取当前用户个人信息（游客态可读，spec §5.y.5）。
     *
     * @param userId 用户ID
     * @return 用户信息 Map（id/username/email/nickname/avatar/role/status/openid/verified/bindEmail/guestShortId）
     */
    Map<String, Object> getProfile(Long userId);

    /**
     * 修改个人信息。
     *
     * @param userId 用户ID
     * @param req    修改内容（nickname / avatar）
     * @return 更新后的用户信息 Map
     */
    Map<String, Object> updateProfile(Long userId, ProfileUpdateReq req);

    /**
     * 获取用户统计。
     *
     * @param userId 用户ID
     * @return 用户统计数据（publishedCount, pendingCount, favoriteCount, reviewCount）
     */
    UserStatsVO getUserStats(Long userId);

    /**
     * 管理后台登录（方案 C，spec §5.y.5）：管理员账号密码 + BCrypt + JWT。
     *
     * @param req 账号 + 密码
     * @return AdminLoginResp{token, username, role}
     */
    AdminLoginResp adminLogin(AdminLoginReq req);

    /**
     * 将 User 实体转换为小程序端用户信息 VO。
     *
     * @param user 用户实体
     * @return 用户信息 VO（camelCase，含 verified/bindEmail/guestShortId）
     */
    UserInfoVO toUserInfo(User user);
}
