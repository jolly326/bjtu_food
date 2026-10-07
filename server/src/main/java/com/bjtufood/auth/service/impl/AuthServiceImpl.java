package com.bjtufood.auth.service.impl;

import com.bjtufood.auth.dto.LoginVO;
import com.bjtufood.auth.dto.ProfileUpdateReq;
import com.bjtufood.auth.dto.UserInfoVO;
import com.bjtufood.auth.constant.UserConst;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.service.AuthService;
import com.bjtufood.auth.service.EmailCodeService;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.wechat.service.WechatService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.auth.support.JwtUtil;
import com.bjtufood.auth.support.VerifyCodeAttemptGuard;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
import com.bjtufood.moderation.service.ContentSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final UserMapper userMapper;
    /**
     * 资料落库事务边界：{@code updateProfile} **只让落库一步进事务**（「先机审（无事务）→ 再落库（开事务）」）——
     * 若事务从方法入口开始，会横跨微信机审的 HTTP 外呼，期间持续占用数据库连接；
     * HikariCP 默认池仅 10 条，并发一高即被占满并拖垮只读请求。
     * 必须是**独立 Bean**：同类自调用不会开启事务。
     */
    private final AuthProfilePersister profilePersister;
    /**
     * 验证码校验与认证写入的事务边界：{@code verifyEmail} **只让写入一步进事务** ——
     * 若从方法入口开始，会横跨「逐条 BCrypt 定位验证码」（最近 20 条 × 约 100ms ≈ 最坏 2 秒），
     * 期间持续占用连接。现拆为「校验（无事务）→ 写入（开事务）」两个事务方法，落在本 Bean 内。
     * 详见 {@link VerifyCodePersister} 类注释。
     */
    private final VerifyCodePersister verifyCodePersister;
    private final EmailCodeService emailCodeService;
    private final JwtUtil jwtUtil;
    private final WechatService wechatService;
    private final ImageUrlUtil imageUrlUtil;
    private final LocalSensitiveFilter localSensitiveFilter;
    private final ContentSecurityService contentSecurityService;
    /**
     * 账号注销执行器（详见 {@link AccountCloser} 类注释）：注销的匿名化写库、验证码清理、
     * 站内通知删除事件与 token 双维度拉黑整体收敛到该 Bean（与本人自注销 / 管理员删除账号同口径）。
     */
    private final AccountCloser accountCloser;
    /**
     * 验证码校验失败计数护栏（A1：防 6 位码暴力枚举，口径见 {@link VerifyCodeAttemptGuard} 类注释）。
     * <p>
     * 刻意放在 final 字段**末尾**：{@code AuthServiceImplTest} 按字段声明顺序显式调用全参构造器。
     */
    private final VerifyCodeAttemptGuard verifyCodeAttemptGuard;

    @Override
    public void createEmailCode(String username) {
        emailCodeService.sendCode(username);
    }

    @Override
    public LoginVO wechatLogin(String code) {
        WechatService.WechatSession session = wechatService.code2Session(code);
        String openid = session.openid();

        User user = userService.getByOpenid(openid);
        if (user == null) {
            user = userService.createWechatGuest(openid);
        }
        if (UserConst.STATUS_DISABLED.equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }
        if (UserConst.STATUS_DELETED.equals(user.getStatus())) {
            throw new BusinessException("账号已注销");
        }
        // 微信登录只读 user（openid / status），不回写任何列
        return toLoginVO(user);
    }

    /**
     * 邮箱验证码认证：<b>先校验（无事务）→ 再写入（开事务）</b>。
     * <p>
     * <b>本方法刻意不加 {@code @Transactional}</b>：事务若从方法入口开始，会横跨
     * 「逐条 BCrypt 定位验证码」这一次 CPU 密集操作（最近 20 条 × 约 100ms ≈ 最坏 2 秒）
     * ⇒ 期间持续占用数据库连接；HikariCP 池仅 20 条，并发一高即被占满并拖垮只读请求。
     * 与 {@code AuthProfilePersister} / {@code ReviewPersister} / {@code FeedbackPersister} 同一口径：
     * <b>事务只包住写入一步</b>。
     * <p>
     * 两个事务方法都落在 {@link VerifyCodePersister}（独立 Bean，Spring 代理事务才生效），
     * 拆分依据见该类注释：验证码的原子消费与跨域归属迁移语义相反，不应同生共死。
     */
    @Override
    public UserInfoVO verifyEmail(String code, Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        User current = userMapper.selectById(userId);
        if (current == null) {
            throw new BusinessException("用户不存在");
        }
        if (UserConst.STATUS_DISABLED.equals(current.getStatus()) || UserConst.STATUS_DELETED.equals(current.getStatus())) {
            throw new BusinessException("账号状态异常，无法认证");
        }

        // 防暴力枚举（A1）：封禁窗口内直接拒绝，不进入 BCrypt 比对。
        // 为何不收窄匹配范围：发码接口是匿名 permitAll，服务端拿不到「谁申请了这条码」的归属信息，
        // 所以只能让「枚举」本身不成立——本护栏（每用户 10 次/15 分钟）+ Controller 的 IP 限频。
        verifyCodeAttemptGuard.assertNotLocked(userId);
        String email;
        try {
            // 校验验证码并原子消费，推导绑定邮箱（purpose=verify、未用、未过期）
            email = verifyCodePersister.consumeAndGetEmail(code);
        } catch (BusinessException e) {
            // 失败计数（含空码/错误/过期/不存在）：达阈值即封禁窗口内 fail-fast
            verifyCodeAttemptGuard.recordFailure(userId);
            throw e;
        }
        verifyCodeAttemptGuard.recordSuccess(userId);

        // 认证写入（此处才开事务）：释放他微信绑定 + 归属迁移 + 历史邮箱账号清理 + 置当前账号 bind_email。
        // 四步同生共死——否则会出现「邮箱已释放但业务数据没迁移」的中间态（数据悬在新旧两账号之间）。
        // 各步实现与判据见 VerifyCodePersister#applyVerifiedBinding。
        verifyCodePersister.applyVerifiedBinding(current, email);

        return toUserInfo(current);
    }

    @Override
    public UserInfoVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toUserInfo(user);
    }

    /**
     * 更新昵称 / 头像。<b>本方法刻意不加 {@code @Transactional}</b>：事务边界收窄到落库一步
     * （{@link AuthProfilePersister#updateNicknameAndAvatar}），使微信机审的外呼期间不占用数据库连接。
     */
    @Override
    public UserInfoVO updateProfile(Long userId, ProfileUpdateReq req) {
        if (!StringUtils.hasText(req.getNickname()) && !StringUtils.hasText(req.getAvatar())) {
            throw new BusinessException("昵称和头像至少填写一项");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 昵称：本地敏感词 + 微信机审（均在无事务状态下完成，不占用数据库连接）
        if (StringUtils.hasText(req.getNickname())) {
            if (localSensitiveFilter.containsSensitive(req.getNickname())) {
                throw new BusinessException("昵称包含敏感内容，请修改后重试");
            }
            // 内容安全检测（产品定稿 昵称变更 msgSecCheck v2，scene=1 资料）。
            // risky 由 checkText 统一拦截（400「内容包含违规信息，请修改后重试」）；
            // openid 为 NULL（历史学号账号）或微信凭据未配置时跳过机审放行（与评价口径一致，报告备案）。
            contentSecurityService.checkText(user.getOpenid(), req.getNickname(), 1);
        }
        // 头像：地址白名单校验（同样在无事务状态下完成）——
        // 仅接受站内相对路径，或走 /upload/cloud-image 链路（已过 imgSecCheck + COS 转存）的正式地址
        if (StringUtils.hasText(req.getAvatar()) && !imageUrlUtil.isValidAvatar(req.getAvatar())) {
            throw new BusinessException("头像地址不合法，仅支持站内资源或已通过内容安检的上传地址");
        }
        // 落库收窄为单一事务：仅更新昵称/头像（局部更新，避免整行覆盖导致并发 lost update）
        profilePersister.updateNicknameAndAvatar(userId, req.getNickname(), req.getAvatar());
        User updated = userMapper.selectById(userId);
        return toUserInfo(updated);
    }

    @Override
    public void deleteAccount(Long userId, String token) {
        // 注销整体（匿名化写库 + 清验证码 + 通知删除事件 + 双维度拉黑）收敛到 AccountCloser：
        // 独立 Bean 上的事务方法（事务边界 / userId 临界区 / 本人与管理员两入口的语义差异见其类注释）。
        accountCloser.close(userId, token, true);
    }

    @Override
    public UserInfoVO toUserInfo(User user) {
        // 字段集恰 5 个：id/username/nickname/avatar/bindEmail；
        // verified（bindEmail 派生冗余）、email/status/guestShortId/createdAt
        // 不在 VO 内且不得回流（见 UserInfoVO 类注释）
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        // username 端上零消费，不出参（账号标识保留在 user 表与 JWT 载荷，供日志）
        vo.setNickname(user.getNickname());
        vo.setAvatar(imageUrlUtil.toAbsoluteUrl(user.getAvatar()));
        vo.setBindEmail(user.getBindEmail());
        return vo;
    }

    // ============================ 私有方法 ============================

    private LoginVO toLoginVO(User user) {
        // JWT 载荷不含 role（user 表无 role 列）：学生态 authorities 由 JwtAuthFilter 固定授予
        String token = jwtUtil.createToken(user.getId(), user.getUsername());
        return new LoginVO(token, toUserInfo(user));
    }

}
