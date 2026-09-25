package com.bjtufood.auth.service;

import com.bjtufood.auth.dto.LoginVO;
import com.bjtufood.auth.dto.ProfileUpdateReq;
import com.bjtufood.auth.dto.UserInfoVO;
import com.bjtufood.auth.entity.User;


/**
 * 认证服务接口（微信登录体系，spec §5.y）
 * <p>
 * 小程序端无账号密码：微信静默登录（wechat-login）→ 游客态（bind_email 为 NULL）；
 * 邮箱验证码认证（verify-email）解锁 UGC 写操作。管理端无登录体系（/admin/** 由 AdminTokenFilter 口令校验，方案 C 已作废）。
 */
public interface AuthService {

    /**
     * 发送邮箱验证码（认证用途 verify，spec §5.y.5）。
     * <p>
     * 校园邮箱由 username 推导，无需调用方传 email / purpose；同邮箱 60s 限频、验证码 10min 有效。
     *
     * @param username 学号/账号（必填）；收件邮箱推导为 {username}@bjtu.edu.cn
     */
    void createEmailCode(String username);

    /**
     * 微信静默登录（spec §5.y.1 / task-01 1.1）。
     * <p>
     * 后端 code2Session 换 openid → 按 user.openid 取号：
     * 存在则返回原账号；不存在则自动建号（游客态 = bind_email 为 NULL）。
     * （user.unionid 已随列退役，2026-09-16 零消费删除，不再回写/补全。）
     *
     * @param code 微信 wx.login 临时凭证
     * @return LoginVO{token, userInfo}
     * @throws com.bjtufood.common.exception.BusinessException code2Session 失败时 400
     */
    LoginVO wechatLogin(String code);

    /**
     * 学号邮箱认证（spec §5.y.3 / task-01 1.3）。
     * <p>
     * 校验验证码 → 按邮箱执行数据迁移合并 / 绑定替换 → 写 bind_email（**认证态唯一写入点**，
     * 非空即已认证）→ 返回更新后 UserInfoVO。
     * 邮箱是唯一迁移 / 绑定凭证；不设解绑入口。
     *
     * @param code   邮箱验证码（对应记录推导绑定邮箱）
     * @param userId 当前微信账号 ID（SecurityUtil 取）
     * @return 更新后 UserInfoVO（bind_email 已写入；JWT 不含 bind_email、实时查库，无需重发 token）
     */
    UserInfoVO verifyEmail(String code, Long userId);

    /**
     * 获取当前用户个人信息（游客态可读，spec §5.y.5）。
     *
     * @param userId 用户ID
     * @return UserInfoVO（id/username/nickname/avatar/bindEmail/createdAt —— 与登录链路字段集严格同构，恰 6 字段）
     */
    UserInfoVO getProfile(Long userId);

    /**
     * 修改个人信息。
     *
     * @param userId 用户ID
     * @param req    修改内容（nickname / avatar）
     * @return 更新后的 UserInfoVO
     */
    UserInfoVO updateProfile(Long userId, ProfileUpdateReq req);

    /**
     * 注销当前登录账号（匿名化，非物理删除，合规硬需求）。
     * <p>
     * 匿名化范围（事务内）：
     * <ul>
     *   <li>user 行：nickname→'已注销用户'；avatar/email/openid/bind_email→NULL（bind_email 清空即
     *       认证态回落为游客态）；status→'deleted'；username 改写为 deleted_{id}
     *       （释放 uk_user_username，保证同一微信可重新静默登录建新游客号）；</li>
     *   <li>review / user_feedback：保留（内容价值 + 评分聚合不破坏），展示昵称经 join user
     *       自然变为「已注销用户」；</li>
     *   <li>notification：随注销物理删除（账号维度过程性数据，注销后无读取方，避免孤儿数据）；</li>
     *   <li>email_verification_code：删除该用户（email / bind_email 匹配）的验证码记录。</li>
     * </ul>
     * token 失效：注销成功后把当前请求 token 拉黑（token 维度）并按 userId 整体拉黑，
     * 复用 {@code TokenBlacklist}（JVM 内存实现、7 天窗口）：单实例未重启期间同用户其余设备
     * 历史 token 立即失效；服务重启后黑名单清空，此时 status=deleted 仍持久拦截重新登录与 UGC 写操作。
     * 终态保护：已注销（status=deleted）用户重复调用返回 400「账号已注销」。
     *
     * @param userId 当前登录用户ID（SecurityUtil 取，不信任前端）
     * @param token  当前请求携带的 JWT（用于注销后立即失效；可空——为空时仅按 userId 拉黑）
     */
    void deleteAccount(Long userId, String token);

    /**
     * 将 User 实体转换为小程序端用户信息 VO。
     *
     * @param user 用户实体
     * @return 用户信息 VO（camelCase，恰 5 字段：id/username/nickname/avatar/bindEmail）
     */
    UserInfoVO toUserInfo(User user);
}
