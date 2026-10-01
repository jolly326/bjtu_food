package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bjtufood.auth.constant.UserConst;
import com.bjtufood.auth.entity.EmailVerificationCode;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.event.UserOwnershipMigratedEvent;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 验证码校验与认证写入（**最小化事务边界**）。
 * <p>
 * 背景：此前 {@code AuthServiceImpl#verifyEmail} 直接标注 {@code @Transactional} —— 事务从<b>方法入口</b>就开始，
 * 而它内部的验证码定位会对<b>最近 20 条未过期验证码逐条 BCrypt 比对</b>
 * （{@code passwordEncoder.matches} 单次约 100ms），最坏情形 <b>20 × 100ms ≈ 2 秒</b>的连接占用。
 * 这与 {@code ReviewPersister} / {@code AuthProfilePersister} / {@code FeedbackPersister} 修掉的是<b>同一类问题</b>
 * （事务横跨慢操作 → 占用连接 → 并发一高即占满池、拖垮只读请求），只是本次的「慢操作」不是 HTTP 外呼，
 * 而是 <b>CPU 密集的密码学比对</b>——此前未被识别，是同一类问题在第四条链路上的遗漏。
 * <p>
 * 修法（与既有三处同构）：<b>先校验（无事务、不占连接）→ 再写入（才开事务）</b>。
 * <p>
 * 校验与认证写入<b>必须</b>分成两个事务方法，理由不止性能：
 * <ul>
 *   <li>验证码的<b>原子消费</b>（{@code UPDATE ... WHERE used_at IS NULL}）须独立提交，
 *       否则两个并发请求可读到同一条未用记录；</li>
 *   <li>认证写入涉及<b>跨域归属迁移</b>（发布 {@code UserOwnershipMigratedEvent}，同步监听器失败须整体回滚）。
 *       二者语义相反：若混在一个事务里，归属迁移失败会连坐回滚掉已成功的验证码消费，
 *       用户重试时码已被吞；而拆开后即便认证写入失败，重新获取验证码即可重试，不会丢凭据。</li>
 * </ul>
 * <b>必须是独立 Bean</b>：Spring 声明式事务靠代理生效，同类自调用不经过代理 ⇒ 事务不会开启。
 */
@Component
@RequiredArgsConstructor
public class VerifyCodePersister {

    private final EmailVerificationCodeMapper emailVerificationCodeMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 校验验证码并<b>原子消费</b>，返回该码绑定的邮箱。
     * <p>
     * 事务边界仅包住末尾那次消费 UPDATE；前面的候选查询与 BCrypt 比对在事务外完成，
     * 最坏 2 秒的比对耗时<b>不占用数据库连接</b>。
     * <p>
     * 匹配口径：按 purpose 全局匹配最近 20 条未用未过期记录，用 BCrypt 逐条比对定位。
     * 之所以要「逐条比对」而非按码直查，是因为发码接口为匿名 permitAll，服务端没有「谁申请了这条码」的归属信息，
     * 且 {@code code_hash} 是 BCrypt 散列、无法反查（详见 {@code VerifyCodeAttemptGuard} 类注释）。
     *
     * @param code 用户输入的 6 位验证码
     * @return 该验证码绑定的邮箱
     * @throws BusinessException 验证码为空 / 不存在或已过期 / 不匹配
     */
    @Transactional(rollbackFor = Exception.class)
    public String consumeAndGetEmail(String code) {
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
     * 认证写入（事务边界包住全部跨域写入）。
     * <p>
     * 收在同一事务的原因：<b>归属迁移要么全成、要么全不成</b>。本方法依次做「释放他微信绑定 →
     * 迁移该账号业务数据 → 标记历史邮箱账号注销 → 置当前账号 bind_email」，
     * 任何一步失败都必须整体回滚，否则会出现「邮箱已被释放但业务数据没迁移」的中间态
     * （旧微信掉了登录态、数据悬在新旧两账号之间）。
     *
     * @param currentUser 发起认证的当前账号实体（认证态唯一写入点为其 bind_email）
     * @param email       验证码绑定（并已消费）的邮箱
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyVerifiedBinding(User currentUser, String email) {
        // 已认证的微信绑定（bind_email = 邮箱）：被他人占用则释放并迁移其业务数据
        User verifiedBinding = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getBindEmail, email));
        if (verifiedBinding != null && !verifiedBinding.getId().equals(currentUser.getId())) {
            // 替换绑定：旧微信 bind_email=NULL（认证态判据即该列非空，清空即回落游客态）。
            // 必须走 LambdaUpdateWrapper 显式 set NULL：updateById 对 null 字段默认不写列，
            // bind_email 无法被清空，会导致唯一键占用不释放、替换绑定失效。
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, verifiedBinding.getId())
                    .set(User::getBindEmail, null));
            publishOwnershipMigrated(verifiedBinding.getId(), currentUser.getId());
        }

        // 历史邮箱注册账号（email = 邮箱，旧账号密码体系）
        User legacyAccount = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (legacyAccount != null && !legacyAccount.getId().equals(currentUser.getId())) {
            // 数据归属转移：旧账号业务数据改挂到当前微信
            publishOwnershipMigrated(legacyAccount.getId(), currentUser.getId());
            // 旧账号清理：标记 deleted 并释放 email 唯一键占用。
            // email 置 NULL 必须显式 set（updateById 忽略 null 字段不写列）：
            // NULL 不占用 uk_user_email 唯一索引，空串则会与其它置 '' 的账号冲突。
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, legacyAccount.getId())
                    .set(User::getStatus, UserConst.STATUS_DELETED)
                    .set(User::getEmail, null));
        }

        // 置当前微信为已认证：**认证态唯一写入点 = bind_email**（无布尔列，派生判据见 AuthStateUtil）
        currentUser.setBindEmail(email);
        userMapper.updateById(currentUser);
    }

    /**
     * 数据归属迁移（spec §5.y.3）：把旧账号 user_id 下的业务数据改挂到新账号。
     * <p>
     * P0-1 架构收口：本方法不再持有 review / feedback / notify 的 Mapper，改为发布
     * {@code UserOwnershipMigratedEvent}，由各域监听器自理本域表（review 独有的
     * 「先清理目标账号已存在的同 dish 冲突行、再改归属」知识一并收敛回 review 域实现）。
     * 监听器为同步 {@code @EventListener} → 仍在 {@link #applyVerifiedBinding} 事务内执行，失败整体回滚。
     */
    private void publishOwnershipMigrated(Long fromUserId, Long toUserId) {
        if (fromUserId == null || toUserId == null || fromUserId.equals(toUserId)) {
            return;
        }
        eventPublisher.publishEvent(new UserOwnershipMigratedEvent(fromUserId, toUserId));
    }
}