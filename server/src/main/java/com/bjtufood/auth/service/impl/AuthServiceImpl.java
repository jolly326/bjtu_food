package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bjtufood.auth.dto.AdminLoginReq;
import com.bjtufood.auth.dto.AdminLoginResp;
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
import com.bjtufood.common.constant.RoleConst;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DateTimeUtil;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JwtUtil;
import com.bjtufood.common.utils.SensitiveFilter;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.entity.ReviewUseful;
import com.bjtufood.review.mapper.ReviewMapper;
import com.bjtufood.review.mapper.ReviewUsefulMapper;
import com.bjtufood.apply.entity.ApplyAction;
import com.bjtufood.apply.mapper.ApplyActionMapper;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.history.entity.ViewLog;
import com.bjtufood.history.mapper.ViewLogMapper;
import com.bjtufood.moment.entity.Moment;
import com.bjtufood.moment.entity.MomentComment;
import com.bjtufood.moment.entity.MomentUseful;
import com.bjtufood.moment.mapper.MomentCommentMapper;
import com.bjtufood.moment.mapper.MomentMapper;
import com.bjtufood.moment.mapper.MomentUsefulMapper;
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
    private final MomentMapper momentMapper;
    private final MomentCommentMapper momentCommentMapper;
    private final MomentUsefulMapper momentUsefulMapper;
    private final FeedbackMapper feedbackMapper;
    private final ApplyActionMapper applyActionMapper;
    private final ViewLogMapper viewLogMapper;
    private final NotificationMapper notificationMapper;
    private final ImageUrlUtil imageUrlUtil;
    private final SensitiveFilter sensitiveFilter;

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
            // 旧账号清理：标记 deleted，释放其 username/email 唯一键占用
            // email 置 NULL 而非空串：多账号统一置 '' 会撞 uk_user_email 唯一索引，NULL 不占用唯一键
            legacyAccount.setStatus("deleted");
            legacyAccount.setEmail(null);
            userMapper.updateById(legacyAccount);
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
    public AdminLoginResp adminLogin(AdminLoginReq req) {
        User user = userService.getByUsername(req.getAccount().trim());
        if (user == null || !RoleConst.isAdmin(user.getRole())
                || !StringUtils.hasText(user.getPassword())
                || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        if ("disabled".equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }
        if ("deleted".equals(user.getStatus())) {
            throw new BusinessException("账号已注销");
        }
        user.setLastLoginAt(DateTimeUtil.now());
        userMapper.updateById(user);
        // 管理端短期 Token：12 小时过期，降低泄露风险（学生端静默登录保持长期，见 toLoginResp）
        String token = jwtUtil.createToken(user.getId(), user.getRole(), user.getUsername(), ADMIN_TOKEN_EXPIRATION_MS);
        return new AdminLoginResp(token, user.getUsername(), user.getRole());
    }

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
    private static final long ADMIN_TOKEN_EXPIRATION_MS = 12 * 60 * 60 * 1000L;

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
                        .orderByDesc(EmailVerificationCode::getCreatedAt));
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
                record.setUsedAt(DateTimeUtil.now());
                emailVerificationCodeMapper.updateById(record);
                return record.getEmail();
            }
        }
        throw new BusinessException("验证码错误");
    }

    /**
     * 释放已被他微信绑定的邮箱：旧微信 verified=0、bind_email=NULL、verified_at=NULL。
     */
    private void releaseVerifiedBinding(User binding) {
        binding.setVerified(0);
        binding.setBindEmail(null);
        binding.setVerifiedAt(null);
        userMapper.updateById(binding);
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

        // review_useful / moment_useful：先清冲突后转移（moment_comment_useful 已随评论点赞下线移除）
        reviewUsefulMapper.delete(new LambdaUpdateWrapper<ReviewUseful>()
                .eq(ReviewUseful::getUserId, fromUserId)
                .inSql(ReviewUseful::getReviewId, "SELECT review_id FROM review_useful WHERE user_id = " + toUserId));
        reviewUsefulMapper.update(null, new LambdaUpdateWrapper<ReviewUseful>()
                .eq(ReviewUseful::getUserId, fromUserId)
                .set(ReviewUseful::getUserId, toUserId));

        momentUsefulMapper.delete(new LambdaUpdateWrapper<MomentUseful>()
                .eq(MomentUseful::getUserId, fromUserId)
                .inSql(MomentUseful::getMomentId, "SELECT moment_id FROM moment_useful WHERE user_id = " + toUserId));
        momentUsefulMapper.update(null, new LambdaUpdateWrapper<MomentUseful>()
                .eq(MomentUseful::getUserId, fromUserId)
                .set(MomentUseful::getUserId, toUserId));

        // apply_action：唯一键 (entity_type,entity_id,apply_type,status)，仅 pending 态可能冲突。
        // 先查出新账号的 pending 申请，删除旧账号同 (entity_type,entity_id,apply_type) 的 pending 申请，再整体改挂。
        List<ApplyAction> toPendingApplies = applyActionMapper.selectList(
                new LambdaQueryWrapper<ApplyAction>()
                        .eq(ApplyAction::getApplicantId, toUserId)
                        .eq(ApplyAction::getStatus, "pending"));
        for (ApplyAction ta : toPendingApplies) {
            applyActionMapper.delete(new LambdaUpdateWrapper<ApplyAction>()
                    .eq(ApplyAction::getApplicantId, fromUserId)
                    .eq(ApplyAction::getStatus, "pending")
                    .eq(ApplyAction::getEntityType, ta.getEntityType())
                    .eq(ApplyAction::getEntityId, ta.getEntityId())
                    .eq(ApplyAction::getApplyType, ta.getApplyType()));
        }
        applyActionMapper.update(null, new LambdaUpdateWrapper<ApplyAction>()
                .eq(ApplyAction::getApplicantId, fromUserId)
                .set(ApplyAction::getApplicantId, toUserId));

        // 无唯一键约束的直接归属改写
        dishMapper.update(null, new LambdaUpdateWrapper<Dish>()
                .eq(Dish::getCreatedBy, fromUserId)
                .set(Dish::getCreatedBy, toUserId));
        momentMapper.update(null, new LambdaUpdateWrapper<Moment>()
                .eq(Moment::getUserId, fromUserId)
                .set(Moment::getUserId, toUserId));
        momentCommentMapper.update(null, new LambdaUpdateWrapper<MomentComment>()
                .eq(MomentComment::getUserId, fromUserId)
                .set(MomentComment::getUserId, toUserId));
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
