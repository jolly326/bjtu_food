package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bjtufood.auth.dto.LoginVO;
import com.bjtufood.auth.dto.ProfileUpdateReq;
import com.bjtufood.auth.dto.UserInfoVO;
import com.bjtufood.auth.constant.UserConst;
import com.bjtufood.auth.entity.EmailVerificationCode;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.service.AuthService;
import com.bjtufood.auth.service.EmailCodeService;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.wechat.service.WechatService;
import com.bjtufood.auth.config.TokenBlacklist;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.auth.support.JwtUtil;
import com.bjtufood.auth.support.VerifyCodeAttemptGuard;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
import com.bjtufood.auth.event.UserAccountClosedEvent;
import com.bjtufood.moderation.service.ContentSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final UserMapper userMapper;
    /**
     * 资料落库事务边界：
     * {@code updateProfile} 的 {@code @Transactional} 原先从方法入口就开始、横跨微信机审的 HTTP 外呼
     * ⇒ 期间一直占用数据库连接；HikariCP 默认池仅 10 条，并发一高即被占满并拖垮只读请求。
     * 现改为「先机审（无事务）→ 再落库（开事务）」。必须是**独立 Bean**：同类自调用不会开启事务。
     */
    private final AuthProfilePersister profilePersister;
    /**
     * 验证码校验与认证写入的事务边界（D1）：原实现里 {@code verifyEmail} 的 {@code @Transactional}
     * 从方法入口就开始，横跨「逐条 BCrypt 定位验证码」（最近 20 条 × 约 100ms ≈ 最坏 2 秒），
     * 期间持续占用连接。现拆为「校验（无事务）→ 写入（开事务）」两个事务方法，落在本 Bean 内。
     * 详见 {@link VerifyCodePersister} 类注释。
     */
    private final VerifyCodePersister verifyCodePersister;
    private final EmailVerificationCodeMapper emailVerificationCodeMapper;
    private final EmailCodeService emailCodeService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final WechatService wechatService;
    /**
     * 跨域写侧出口（P0-1 架构收口）：auth 不再注入 review / feedback / notify 的 Mapper 直改他域表，
     * 改为在事务内发布领域事件，各域监听器自理本域表。
     * <p>
     * 监听器均为同步 {@code @EventListener}（非 AFTER_COMMIT）→ 仍在调用方事务内执行，
     * 任一环节失败整体回滚，与原内联写库的事务边界逐字一致。
     */
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUrlUtil imageUrlUtil;
    private final LocalSensitiveFilter localSensitiveFilter;
    private final ContentSecurityService contentSecurityService;
    private final TokenBlacklist tokenBlacklist;
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
        // last_login_at 写入点已随列退役
        // user.unionid 已随列退役，微信登录仅消费 openid，无需回写
        return toLoginVO(user);
    }

    /**
     * 邮箱验证码认证：<b>先校验（无事务）→ 再写入（开事务）</b>。
     * <p>
     * <b>本方法刻意不加 {@code @Transactional}</b>（D1）：原实现的事务从方法入口就开始，
     * 横跨「逐条 BCrypt 定位验证码」这一次 CPU 密集操作（最近 20 条 × 约 100ms ≈ 最坏 2 秒）
     * ⇒ 期间持续占用数据库连接；HikariCP 池仅 20 条，并发一高即被占满并拖垮只读请求。
     * 这与 {@code AuthProfilePersister}（横跨微信机审外呼）、{@code ReviewPersister} /
     * {@code FeedbackPersister}（横跨微信机审外呼）是<b>同一类问题</b>，此前只在后三处修过，
     * 此处是同型问题在第四条链路上的遗漏。
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
        // 各步实现与判据见 VerifyCodePersister#applyVerifiedBinding（逐字迁移，行为不变）。
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
        // 头像：地址白名单校验（同样在无事务状态下完成）
        if (StringUtils.hasText(req.getAvatar()) && !imageUrlUtil.isValidAvatar(req.getAvatar())) {
            throw new BusinessException("头像地址不合法，仅支持站内资源或微信云存储");
        }
        // 落库收窄为单一事务：仅更新昵称/头像（局部更新，避免整行覆盖导致并发 lost update）
        profilePersister.updateNicknameAndAvatar(userId, req.getNickname(), req.getAvatar());
        User updated = userMapper.selectById(userId);
        return toUserInfo(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long userId, String token) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "请先登录");
        }
        // 终态保护：已注销用户重复调用返回 400「账号已注销」
        // （场景：服务重启后 TokenBlacklist 清空，同用户其他有效 token 再次到达；正常场景已被过滤器 401 拦截）
        if (UserConst.STATUS_DELETED.equals(user.getStatus())) {
            throw new BusinessException("账号已注销");
        }
        if (UserConst.STATUS_DISABLED.equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用，无法注销");
        }

        // 匿名化 user 行（非物理删除）。必须用 LambdaUpdateWrapper 显式 set NULL：
        // updateById 默认 NOT_NULL 策略对 null 字段不写列，avatar/email/openid 等无法被清空。
        // · username → deleted_{id}：释放 uk_user_username 唯一键占用。openid 置 NULL 解绑后，
        //   同一微信重新静默登录会按 username='wx_'+openid 尾 16 位建新游客号，若保留旧 username
        //   将撞唯一键导致「微信登录创建账号失败」；deleted_{id} 不含任何个人信息且唯一。
        // · openid → NULL：解绑微信身份，允许同一微信重新建号。
        //   （user.password / user.unionid 列已于零消费退役，无需再置空。）
        // · email → NULL：释放 uk_user_email 唯一键占用（NULL 不参与唯一索引）。
        // · bind_email → NULL：解绑认证关系（认证态判据即该列非空，清空即回落游客态），
        //   避免 verifyEmail 的 getByBindEmail 命中已注销账号导致后续认证走「替换绑定」歧义分支。
        // · nickname → '已注销用户'：review/user_feedback 保留且展示昵称经 join user 取本字段，
        //   历史内容自然匿名化，评分聚合不破坏。
        // · status → 'deleted'：与 wechatLogin/adminLogin 的登录拦截、RequireVerifiedAspect 的
        //   UGC 写拦截形成持久化兜底（黑名单重启清空后仍有效）。
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getNickname, "已注销用户")
                .set(User::getUsername, "deleted_" + userId)
                .set(User::getAvatar, null)
                .set(User::getEmail, null)
                .set(User::getOpenid, null)
                .set(User::getBindEmail, null)
                .set(User::getStatus, UserConst.STATUS_DELETED));

        // email_verification_code 按该用户邮箱删除（表无 user_id 列，以 email 匹配）；
        // email 与 bind_email 可能不同（迁移/替换绑定场景），两批都清，避免残留验证码在他端被消费。
        deleteVerifyCodesByEmail(user.getEmail());
        deleteVerifyCodesByEmail(user.getBindEmail());

        // 系统通知：账号维度的过程性数据，注销后账号不可再进入、无任何读取方，
        // 保留即孤儿数据只增不减，故随注销物理删除（与 review / user_feedback「内容价值」保留口径区分）。
        // P0-1：改为发布账号注销事件，由 notify 域监听器硬删该用户全部站内消息
        // （发布点即原 notificationMapper.delete(...) 的位置，同步监听 → 仍在本事务内，失败整体回滚）。
        eventPublisher.publishEvent(new UserAccountClosedEvent(userId));

        // token 立即失效（复用 TokenBlacklist，与管理员禁用同一机制）：
        // · token 维度：精确拉黑当前请求 token，JwtAuthFilter 命中后 401「账号已注销，请重新登录」；
        // · userId 维度：兜底拉黑同用户其余设备的历史 token（注销者拿不到那些 token 明文）。
        tokenBlacklist.revoke(token);
        tokenBlacklist.revokeUser(userId);
    }

    /**
     * 删除指定邮箱的验证码记录（邮箱为空时跳过）。
     */
    private void deleteVerifyCodesByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        emailVerificationCodeMapper.delete(new LambdaQueryWrapper<EmailVerificationCode>()
                .eq(EmailVerificationCode::getEmail, email));
    }

    @Override
    public UserInfoVO toUserInfo(User user) {
        // 字段集恰 5 个：id/username/nickname/avatar/bindEmail；
        // verified（bindEmail 派生冗余）、email/status/guestShortId/createdAt
        // 已从 VO 删除且不得回流（见 UserInfoVO 类注释）
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
        // JWT 载荷不含 role（role 列已退役）：学生态 authorities 由 JwtAuthFilter 固定授予
        String token = jwtUtil.createToken(user.getId(), user.getUsername());
        return new LoginVO(token, toUserInfo(user));
    }

}
