package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bjtufood.auth.config.TokenBlacklist;
import com.bjtufood.auth.constant.UserConst;
import com.bjtufood.auth.entity.EmailVerificationCode;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.event.UserAccountClosedEvent;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号注销执行器：本人自注销（{@code DELETE /auth/account}）与管理员删除账号
 * （{@code DELETE /admin/users/{id}}）共用的<b>唯一实现</b>（匿名化口径判据唯一真源）。
 * <p>
 * 两条入口的差异仅在两点：① 存在性错误码（本人 401「请先登录」不泄露存在性；管理员
 * 4001「用户不存在」）；② token 维度拉黑（本人注销持有当前请求 token 可精确拉黑；
 * 管理端拿不到对方 token，仅 userId 维度）。
 * <p>
 * <b>事务边界</b>：{@code close} 是<b>独立 Bean 上的事务方法</b>（同类私有方法自调用绕过代理，
 * 事务不生效）；「读现状 → 匿名化写库 → 清验证码 → 发事件 → 写黑名单」整体在同一事务内，
 * 任一环节失败整体回滚。锁在事务内获取（取锁 → 事务内多条写 → 写黑名单 → 释放锁 → 提交），
 * 同一 userId 的 DB 写由行锁排队，且黑名单写紧随临界区内的 DB 写之后
 * ⇒ 终态仍是「最后一个临界区说了算」。
 */
@Component
@RequiredArgsConstructor
public class AccountCloser {

    private final UserMapper userMapper;
    private final EmailVerificationCodeMapper emailVerificationCodeMapper;
    /**
     * 跨域写侧出口（P0-1 架构收口）：auth <b>不</b>注入 notify 域的 Mapper 直改他域表，
     * 只在事务内发布领域事件，由各域监听器自理本域表。
     * <p>
     * 监听器均为同步 {@code @EventListener}（非 AFTER_COMMIT）→ 仍在调用方事务内执行，
     * 任一环节失败整体回滚（与直接在本事务内写库的事务边界等价）。
     */
    private final ApplicationEventPublisher eventPublisher;
    private final TokenBlacklist tokenBlacklist;
    /**
     * 「DB status 写 + TokenBlacklist 写」的临界区（详见 {@link UserStateWriteLock} 类注释）。
     * 与管理员启停（{@code UserServiceImpl#updateStatus}）、解绑邮箱
     * （{@code UserServiceImpl#unbindEmail}）共用同一实例：
     * 同一 userId 的「注销 / 启停 / 解绑」三条写入口互相串行，
     * 避免终态出现「DB = active 且该 userId 仍在拉黑中」。
     */
    private final UserStateWriteLock userStateWriteLock;

    /**
     * 注销账号（匿名化，非物理删除）。
     *
     * @param userId      被注销账号（本人路径来自 JWT；管理员路径来自路径参数）
     * @param token       当前请求 token（本人路径用于精确拉黑；管理员路径为 {@code null}，
     *                    {@code TokenBlacklist#revoke} 对空值安全跳过）
     * @param selfService 是否本人自注销：{@code true} 时用户不存在报 401「请先登录」
     *                    （不泄露「账号不存在」）；{@code false} 时报 4001「用户不存在」
     * @throws BusinessException 401（本人未登录 / 账号不存在）、4001（管理员路径账号不存在）、
     *         400「账号已注销」（终态保护：黑名单重启清空后同用户其他有效 token 再次到达的兜底）、
     *         400「账号已被禁用，无法注销」（禁用账号必须先由管理员恢复启用或走本人申诉，不兼并两条状态写）
     */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long userId, String token, boolean selfService) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        userStateWriteLock.run(userId, () -> doClose(userId, token, selfService));
    }

    /**
     * {@link #close} 的临界区主体：仅在 userId 临界区内同步执行（同一线程 ⇒ 仍在
     * {@code close} 的事务中），异常原样上抛以触发整体回滚。
     */
    private void doClose(Long userId, String token, boolean selfService) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw selfService ? new BusinessException(401, "请先登录") : new BusinessException(4001, "用户不存在");
        }
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
        // P0-1：发布账号注销事件，由 notify 域监听器硬删该用户全部站内消息
        // （同步监听 → 仍在本事务内执行，失败整体回滚）。
        eventPublisher.publishEvent(new UserAccountClosedEvent(userId));

        // token 立即失效（复用 TokenBlacklist，与管理员禁用同一机制）：
        // · token 维度：本人注销时精确拉黑当前请求 token，JwtAuthFilter 命中后 401「账号已注销，请重新登录」
        //   （管理员路径无对方 token 明文，revoke 对 null 安全跳过）；
        // · userId 维度：拉黑该用户全部历史 token（含其余设备的）。
        tokenBlacklist.revoke(token);
        tokenBlacklist.revokeUser(userId);
    }

    /**
     * 删除指定邮箱的验证码记录（邮箱为空时跳过）。
     */
    private void deleteVerifyCodesByEmail(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        emailVerificationCodeMapper.delete(new LambdaQueryWrapper<EmailVerificationCode>()
                .eq(EmailVerificationCode::getEmail, email));
    }
}
