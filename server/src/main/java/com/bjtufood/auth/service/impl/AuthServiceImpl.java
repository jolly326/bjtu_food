package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bjtufood.auth.dto.LoginResp;
import com.bjtufood.auth.dto.ProfileUpdateReq;
import com.bjtufood.auth.dto.UserInfoVO;
import com.bjtufood.auth.entity.EmailVerificationCode;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.service.AuthService;
import com.bjtufood.auth.service.EmailCodeService;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.auth.service.WechatService;
import com.bjtufood.auth.config.TokenBlacklist;
import com.bjtufood.common.constant.RoleConst;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DateTimeUtil;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JwtUtil;
import com.bjtufood.common.utils.SensitiveFilter;
import com.bjtufood.content.security.ContentSecurityService;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.entity.ReviewUseful;
import com.bjtufood.review.mapper.ReviewMapper;
import com.bjtufood.review.mapper.ReviewUsefulMapper;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.history.entity.ViewLog;
import com.bjtufood.history.mapper.ViewLogMapper;
import com.bjtufood.notify.entity.Notification;
import com.bjtufood.notify.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final UserMapper userMapper;
    private final EmailVerificationCodeMapper emailVerificationCodeMapper;
    private final EmailCodeService emailCodeService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final WechatService wechatService;
    private final ReviewMapper reviewMapper;
    private final ReviewUsefulMapper reviewUsefulMapper;
    private final DishMapper dishMapper;
    private final FeedbackMapper feedbackMapper;
    private final ViewLogMapper viewLogMapper;
    private final NotificationMapper notificationMapper;
    private final ImageUrlUtil imageUrlUtil;
    private final SensitiveFilter sensitiveFilter;
    private final ContentSecurityService contentSecurityService;
    private final TokenBlacklist tokenBlacklist;

    @Override
    public void createEmailCode(String username, String email, String purpose) {
        emailCodeService.sendCode(username, email, purpose);
    }

    @Override
    public LoginResp wechatLogin(String code) {
        WechatService.WechatSession session = wechatService.code2Session(code);
        String openid = session.openid();

        User user = userService.getByOpenid(openid);
        if (user == null) {
            user = createWechatGuest(session);
        }
        if ("disabled".equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }
        if ("deleted".equals(user.getStatus())) {
            throw new BusinessException("账号已注销");
        }
        user.setLastLoginAt(DateTimeUtil.now());
        // 补全 unionid：已建账号首登时微信未必返回 unionid，后续补全（幂等，不影响唯一键）
        if (!StringUtils.hasText(user.getUnionid()) && StringUtils.hasText(session.unionid())) {
            user.setUnionid(session.unionid());
        }
        userMapper.updateById(user);
        return toLoginResp(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResp verifyEmail(String code, Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        User current = userMapper.selectById(userId);
        if (current == null) {
            throw new BusinessException("用户不存在");
        }
        if ("disabled".equals(current.getStatus()) || "deleted".equals(current.getStatus())) {
            throw new BusinessException("账号状态异常，无法认证");
        }

        // 校验验证码并推导绑定邮箱（验证码记录 purpose=verify、未用、未过期）
        String email = consumeVerifyCodeAndGetEmail(code);

        // 已认证的微信绑定（bind_email = 邮箱）
        User verifiedBinding = userService.getByBindEmail(email);
        if (verifiedBinding != null && !verifiedBinding.getId().equals(current.getId())) {
            // 替换绑定：旧微信 verified=0 / bind_email=NULL / verified_at=NULL；业务数据归属迁移到当前微信
            releaseVerifiedBinding(verifiedBinding);
            migrateOwnership(verifiedBinding.getId(), current.getId());
        }

        // 历史邮箱注册账号（email = 邮箱，旧账号密码体系）
        User legacyAccount = userService.getByEmail(email);
        if (legacyAccount != null && !legacyAccount.getId().equals(current.getId())) {
            // 数据归属转移：旧账号业务数据改挂到当前微信
            migrateOwnership(legacyAccount.getId(), current.getId());
            // 旧账号清理：标记 deleted 并释放 email 唯一键占用。
            // email 置 NULL 必须显式 set（updateById 忽略 null 字段不写列）：
            // NULL 不占用 uk_user_email 唯一索引，空串则会与其它置 '' 的账号冲突。
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, legacyAccount.getId())
                    .set(User::getStatus, "deleted")
                    .set(User::getEmail, null));
        }

        // 置当前微信为已认证
        current.setVerified(1);
        current.setBindEmail(email);
        current.setVerifiedAt(DateTimeUtil.now());
        userMapper.updateById(current);

        return toLoginResp(current);
    }

    @Override
    public Map<String, Object> getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return buildProfileMap(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateProfile(Long userId, ProfileUpdateReq req) {
        if (!StringUtils.hasText(req.getNickname()) && !StringUtils.hasText(req.getAvatar())) {
            throw new BusinessException("昵称和头像至少填写一项");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 使用 LambdaUpdateWrapper 仅更新昵称/头像，避免把整行（含 password 哈希、verified）
        // 重新写回，导致与「修改密码」并发时产生 lost update。
        LambdaUpdateWrapper<User> updater = new LambdaUpdateWrapper<>();
        updater.eq(User::getId, userId);
        if (StringUtils.hasText(req.getNickname())) {
            if (sensitiveFilter.containsSensitive(req.getNickname())) {
                throw new BusinessException("昵称包含敏感内容，请修改后重试");
            }
            // 内容安全检测（产品定稿 2026-09-13：昵称变更 msgSecCheck v2，scene=1 资料）。
            // risky 由 checkText 统一拦截（400「内容包含违规信息，请修改后重试」）；
            // openid 为 NULL（历史学号账号）或微信凭据未配置时跳过机审放行（与评价口径一致，报告备案）。
            contentSecurityService.checkText(user.getOpenid(), req.getNickname(), 1);
            updater.set(User::getNickname, req.getNickname());
        }
        if (StringUtils.hasText(req.getAvatar())) {
            if (!imageUrlUtil.isValidAvatar(req.getAvatar())) {
                throw new BusinessException("头像地址不合法，仅支持站内资源或微信云存储");
            }
            updater.set(User::getAvatar, req.getAvatar());
        }
        userMapper.update(updater);
        User updated = userMapper.selectById(userId);
        return buildProfileMap(updated);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            throw new BusinessException("当前账号未设置密码，无法修改");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
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
        // 幂等保护：已注销用户重复调用返回 400「账号已注销」
        // （场景：服务重启后 TokenBlacklist 清空，同用户其他有效 token 再次到达；正常场景已被过滤器 401 拦截）
        if ("deleted".equals(user.getStatus())) {
            throw new BusinessException("账号已注销");
        }
        if ("disabled".equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用，无法注销");
        }

        // 匿名化 user 行（非物理删除）。必须用 LambdaUpdateWrapper 显式 set NULL：
        // updateById 默认 NOT_NULL 策略对 null 字段不写列，avatar/email/password/openid 等无法被清空。
        // · username → deleted_{id}：释放 uk_user_username 唯一键占用。openid 置 NULL 解绑后，
        //   同一微信重新静默登录会按 username='wx_'+openid 尾 16 位建新游客号，若保留旧 username
        //   将撞唯一键导致「微信登录创建账号失败」；deleted_{id} 不含任何个人信息且唯一。
        // · openid/unionid → NULL：解绑微信身份，允许同一微信重新建号（unionid 同属微信身份标识，
        //   新号登录时 wechatLogin 会自动重新补全，匿名化更彻底）。
        // · email → NULL：释放 uk_user_email 唯一键占用（NULL 不参与唯一索引）。
        // · password → NULL：管理后台密码登录体系随即不可用。
        // · bind_email/verified_at → NULL、verified → 0：解绑认证关系，避免 verifyEmail 的
        //   getByBindEmail 命中已注销账号导致后续认证走「替换绑定」歧义分支。
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
                .set(User::getPassword, null)
                .set(User::getOpenid, null)
                .set(User::getUnionid, null)
                .set(User::getVerified, 0)
                .set(User::getBindEmail, null)
                .set(User::getVerifiedAt, null)
                .set(User::getStatus, "deleted"));

        // email_verification_code 按该用户邮箱删除（表无 user_id 列，以 email 匹配）；
        // email 与 bind_email 可能不同（迁移/替换绑定场景），两批都清，避免残留验证码在他端被消费。
        deleteVerifyCodesByEmail(user.getEmail());
        deleteVerifyCodesByEmail(user.getBindEmail());

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

    // 管理后台登录（adminLogin）已随管理端账号体系一并移除（2026-09-13 定型：后台无登录，
    // 管理端接口由 AdminTokenFilter 的环境变量口令 ADMIN_TOKEN 校验保护）。

    @Override
    public UserInfoVO toUserInfo(User user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setAvatar(imageUrlUtil.toAbsoluteUrl(user.getAvatar()));
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setVerified(Integer.valueOf(1).equals(user.getVerified()));
        vo.setBindEmail(user.getBindEmail());
        vo.setGuestShortId(buildGuestShortId(user.getId()));
        return vo;
    }

    // ============================ 私有方法 ============================

    private LoginResp toLoginResp(User user) {
        String token = jwtUtil.createToken(user.getId(), user.getRole(), user.getUsername());
        return new LoginResp(token, toUserInfo(user));
    }

    private Map<String, Object> buildProfileMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        map.put("nickname", user.getNickname());
        map.put("avatar", imageUrlUtil.toAbsoluteUrl(user.getAvatar()));
        map.put("role", user.getRole());
        map.put("status", user.getStatus());
        map.put("verified", Integer.valueOf(1).equals(user.getVerified()));
        map.put("bindEmail", user.getBindEmail());
        map.put("guestShortId", buildGuestShortId(user.getId()));
        return map;
    }

    /**
     * 游客短标识：食客 + ID 尾 4 位（spec §5.y.4 游客标识）。
     */
    private String buildGuestShortId(Long userId) {
        String id = String.valueOf(userId);
        String tail = id.length() > 4 ? id.substring(id.length() - 4) : id;
        return "食客" + tail;
    }

    /**
     * 管理后台 Token 过期时长：12 小时（毫秒）。
     * <p>
     * 管理端凭据泄露面小但危害大，短期过期降低风险；
     * 学生端静默登录保持长期（见 toLoginResp），两者策略分离。
     */

    /**
     * 新建微信游客账号（verified=0）。
     */
    private User createWechatGuest(WechatService.WechatSession session) {
        String openid = session.openid();
        User user = new User();
        user.setOpenid(openid);
        user.setUnionid(session.unionid());
        // 游客建号：username = wx_+openid 尾 16 位（保证唯一且不含敏感完整 openid）
        String tail = openid.length() > 16 ? openid.substring(openid.length() - 16) : openid;
        user.setUsername("wx_" + tail);
        user.setNickname("食客新友");
        user.setRole(RoleConst.STUDENT);
        user.setStatus("active");
        user.setVerified(0);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发下 openid 唯一键兜底：重新查询已有账号
            User existed = userService.getByOpenid(openid);
            if (existed != null) {
                return existed;
            }
            throw new BusinessException("微信登录创建账号失败，请重试");
        }
        // 默认昵称可用后置为短标识（用建号后自增 ID）
        user.setNickname(buildGuestShortId(user.getId()));
        userMapper.updateById(user);
        return user;
    }

    /**
     * 消费验证码并推导绑定邮箱。
     * <p>
     * 入参仅 code，故遍历未使用、未过期、purpose=verify 的验证码记录，
     * 用 BCrypt 匹配定位邮箱并置 used_at。未命中则视为错误/过期。
     */
    private String consumeVerifyCodeAndGetEmail(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("验证码不能为空");
        }
        List<EmailVerificationCode> records = emailVerificationCodeMapper.selectList(
                new LambdaQueryWrapper<EmailVerificationCode>()
                        .eq(EmailVerificationCode::getPurpose, "verify")
                        .isNull(EmailVerificationCode::getUsedAt)
                        .gt(EmailVerificationCode::getExpiresAt, DateTimeUtil.now())
                        .orderByDesc(EmailVerificationCode::getCreatedAt)
                        // 性能防护：验证码 10 分钟内有效，正常活跃未用验证码极少，
                        // 限定最近 20 条避免验证码量增长时全表 BCrypt 扫描（每条 ~100ms）
                        .last("LIMIT 20"));
        if (records.isEmpty()) {
            throw new BusinessException("验证码不存在或已过期");
        }
        for (EmailVerificationCode record : records) {
            boolean matched;
            try {
                matched = passwordEncoder.matches(code, record.getCodeHash());
            } catch (IllegalArgumentException e) {
                matched = false;
            }
            if (matched) {
                // M1 修复：原子消费验证码（UPDATE ... WHERE used_at IS NULL）。
                // 仅当影响行数=1 才视为本次成功消费，避免并发窗口内同一验证码被重复使用两次
                // （先读未用→后置 used 的 TOCTOU）。
                int used = emailVerificationCodeMapper.update(new LambdaUpdateWrapper<EmailVerificationCode>()
                        .eq(EmailVerificationCode::getId, record.getId())
                        .isNull(EmailVerificationCode::getUsedAt)
                        .set(EmailVerificationCode::getUsedAt, DateTimeUtil.now()));
                if (used > 0) {
                    return record.getEmail();
                }
                // 已被并发消费：继续尝试下一条（实际几乎不会出现第二条匹配），全部未抢到则报错
            }
        }
        throw new BusinessException("验证码错误");
    }

    /**
     * 释放已被他微信绑定的邮箱：旧微信 verified=0、bind_email=NULL、verified_at=NULL。
     * <p>
     * 必须走 LambdaUpdateWrapper 显式 set NULL：updateById 对 null 字段默认不写列，
     * bind_email/verified_at 无法被清空，会导致唯一键占用不释放、替换绑定失效。
     */
    private void releaseVerifiedBinding(User binding) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, binding.getId())
                .set(User::getVerified, 0)
                .set(User::getBindEmail, null)
                .set(User::getVerifiedAt, null));
    }

    /**
     * 数据归属迁移（spec §5.y.3）：把旧账号 user_id/created_by 下的业务数据改挂到新账号。
     * <p>
     * 仅在 {@link #verifyEmail}（已标注 @Transactional）内部被同实例调用，属自调用，
     * 不单独开启事务，统一并入外层事务回滚边界。若被外部 Bean 调用需自行加事务。
     * 对带唯一键的表（review 的 user+dish、各 useful 表）先清理新账号已存在的冲突行（保留新账号记录），
     * 再执行归属改写，避免 DuplicateKey 中断事务。
     */
    protected void migrateOwnership(Long fromUserId, Long toUserId) {
        if (fromUserId == null || toUserId == null || fromUserId.equals(toUserId)) {
            return;
        }
        // review：若新账号已对该 dish 有评价，删除旧账号同 dish 评价（保留新账号）
        reviewMapper.delete(new LambdaUpdateWrapper<Review>()
                .eq(Review::getUserId, fromUserId)
                .inSql(Review::getDishId, "SELECT dish_id FROM review WHERE user_id = " + toUserId));
        reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                .eq(Review::getUserId, fromUserId)
                .set(Review::getUserId, toUserId));

        // review_useful：先清冲突后转移
        reviewUsefulMapper.delete(new LambdaUpdateWrapper<ReviewUseful>()
                .eq(ReviewUseful::getUserId, fromUserId)
                .inSql(ReviewUseful::getReviewId, "SELECT review_id FROM review_useful WHERE user_id = " + toUserId));
        reviewUsefulMapper.update(null, new LambdaUpdateWrapper<ReviewUseful>()
                .eq(ReviewUseful::getUserId, fromUserId)
                .set(ReviewUseful::getUserId, toUserId));

        // 无唯一键约束的直接归属改写
        dishMapper.update(null, new LambdaUpdateWrapper<Dish>()
                .eq(Dish::getCreatedBy, fromUserId)
                .set(Dish::getCreatedBy, toUserId));
        feedbackMapper.update(null, new LambdaUpdateWrapper<Feedback>()
                .eq(Feedback::getUserId, fromUserId)
                .set(Feedback::getUserId, toUserId));
        viewLogMapper.update(null, new LambdaUpdateWrapper<ViewLog>()
                .eq(ViewLog::getUserId, fromUserId)
                .set(ViewLog::getUserId, toUserId));
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, fromUserId)
                .set(Notification::getUserId, toUserId));
    }
}
