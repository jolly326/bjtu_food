package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.dto.UserBriefVO;
import com.bjtufood.auth.dto.UserOverviewVO;
import com.bjtufood.auth.dto.UserVO;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.constant.UserConst;
import com.bjtufood.auth.entity.EmailVerificationCode;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.audit.AuditSnapshot;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.auth.support.AuthStateUtil;
import com.bjtufood.common.utils.ImageUrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 建号占位昵称：`nickname` 列为 NOT NULL，须先写占位值，拿到自增 id 后再于同一事务内回填 */
    private static final String NICKNAME_PLACEHOLDER = "食客新友";

    private final UserMapper userMapper;
    /** 解绑邮箱时清理该绑定邮箱的历史验证码（表无 user_id 列，按 email 匹配删除） */
    private final EmailVerificationCodeMapper emailVerificationCodeMapper;

    @Override
    public UserOverviewVO countOverview() {
        UserOverviewVO vo = new UserOverviewVO();
        // 概况是「平台规模」：已注销用户不计入；已认证以 bind_email 非空为唯一判据（与端上认证态同源）
        vo.setUserCount(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ne(User::getStatus, UserConst.STATUS_DELETED)));
        vo.setVerifiedUserCount(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ne(User::getStatus, UserConst.STATUS_DELETED)
                .isNotNull(User::getBindEmail)));
        return vo;
    }
    private final ImageUrlUtil imageUrlUtil;
    private final com.bjtufood.auth.config.TokenBlacklist tokenBlacklist;
    /**
     * 「DB status 写 + TokenBlacklist 写」的临界区（详见 {@link UserStateWriteLock} 类注释）。
     * 与 {@code AccountCloser#close}（本人注销 / 管理员删除账号）共用同一实例，
     * 故同一 userId 的「启停 / 解绑邮箱 / 注销」三条写入口互相串行。
     */
    private final UserStateWriteLock userStateWriteLock;
    /** 账号注销执行器（本人自注销与管理员删除账号的唯一实现，口径见其类注释） */
    private final AccountCloser accountCloser;

    @Override
    public IPage<UserVO> listUsers(int page, int pageSize, String status, String keyword) {
        int[] p = com.bjtufood.common.utils.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        // C2：关键词 = 昵称 / 账号（email）/ 绑定邮箱 三路模糊（与列表页「按人定位」用途对齐）。
        // 该形态无法走索引（前置通配），与 client / 菜品搜索同属已知取舍；用户量级下可接受。
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(StringUtils.hasText(status), User::getStatus, status)
                .and(kw != null, w -> w.like(User::getNickname, kw)
                        .or().like(User::getEmail, kw)
                        .or().like(User::getBindEmail, kw))
                .orderByDesc(User::getCreatedAt);
        return userMapper.selectPage(new Page<>(page, pageSize), wrapper).convert(this::toVO);
    }

    @Override
    public User getByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }

    @Override
    public User getByOpenid(String openid) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
    }

    @Override
    public User getByBindEmail(String bindEmail) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getBindEmail, bindEmail));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User createWechatGuest(String openid) {
        User user = new User();
        user.setOpenid(openid);
        // 游客建号：username = wx_+openid 尾 16 位（保证唯一且不含敏感完整 openid）
        String tail = openid.length() > 16 ? openid.substring(openid.length() - 16) : openid;
        user.setUsername("wx_" + tail);
        // 最终昵称含自增 id 尾 4 位，而本列 NOT NULL → 先写占位值，插入后回填（同事务）
        user.setNickname(NICKNAME_PLACEHOLDER);
        user.setStatus(UserConst.STATUS_ACTIVE);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发下 openid 唯一键兜底：重新查询已被抢先创建的账号（本方法整体回滚，不留半成品行）
            User existed = getByOpenid(openid);
            if (existed != null) {
                return existed;
            }
            throw new BusinessException("微信登录创建账号失败，请重试");
        }
        // 回填前判等（D5）：仅当昵称仍为占位值时才回填，避免覆盖任何已存在的用户昵称
        User fresh = userMapper.selectById(user.getId());
        if (fresh != null && NICKNAME_PLACEHOLDER.equals(fresh.getNickname())) {
            String nickname = buildGuestNickname(user.getId());
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .set(User::getNickname, nickname));
            fresh.setNickname(nickname);
        }
        return fresh != null ? fresh : user;
    }

    @Override
    public void updateStatus(Long id, String status) {
        // 枚举校验：本接口契约仅允许 active/disabled（见 docs/api/web/users.md），
        // 非法值（含 deleted）一律 400，避免垃圾值直接落库
        if (!UserConst.STATUS_ACTIVE.equals(status) && !UserConst.STATUS_DISABLED.equals(status)) {
            throw new BusinessException("非法的状态：" + status);
        }
        // 「读现状 → 写 DB → 同步黑名单」必须整体落在同一 userId 临界区内：
        // 否则并发「禁用 / 启用」交错可得 DB 终态 active 而该 userId 仍在拉黑中，该用户连重新登录
        // 换到的新 token 也会被 JwtAuthFilter 一律 401（重启或 7 天窗口前不自愈）。
        // 同一 userId 的注销路径（AccountCloser#close）持有同一把锁，故「启用 vs 注销」亦不交错。
        userStateWriteLock.run(id, () -> {
            // 存在性校验（契约 4001「用户不存在」）；读与后续写同处临界区，避免读到的现状事后被推翻
            User current = userMapper.selectById(id);
            if (current == null) {
                throw new BusinessException(4001, "用户不存在");
            }
            // 审计变更前后值：启停是账号能力边界的跃迁，快照使「谁把谁禁用了」可精确追溯
            AuditSnapshot.before(current);
            // 说明：管理端单一管理员模型（无角色区分），用户状态变更不做「禁止操作自身 / 越权」判定；
            // /admin/** 整体由 AdminAuthFilter 的管理端 JWT 校验保护。
            // 只写 status 一列（不整行回写）：整行回写会把昵称 / 头像等列一并写回，
            // 与本人 PUT /auth/profile 的局部更新并发时互相覆盖（lost update）。
            // updated_at 由 DB 时钟维护（见 docs/schema/README.md §时间戳写入来源）
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, id)
                    .set(User::getStatus, status));
            // 禁用后该用户已签发的 token 必须立即失效（否则改了状态仍能带旧 token 访问）；
            // 恢复 active 时解除拉黑，使其可正常登录使用。
            // （deleted 状态由 AccountCloser 的注销路径写入，不经本接口）
            if (UserConst.STATUS_DISABLED.equals(status)) {
                tokenBlacklist.revokeUser(id);
            } else {
                tokenBlacklist.restoreUser(id);
            }
            // 变更后快照（before 已在改动前序列化，此处覆盖新状态不影响已登记的旧值）
            current.setStatus(status);
            AuditSnapshot.after(current);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindEmail(Long id) {
        // 「读现状 → 置空 bind_email → 清验证码」整体落在同一 userId 临界区：
        // 与启停 / 注销（AccountCloser#close）对同一账号的写互不交错。
        userStateWriteLock.run(id, () -> doUnbindEmail(id));
    }

    /**
     * {@link #unbindEmail} 的临界区主体。
     * <p>
     * 认证态唯一判据 = {@code bind_email} 非空（不进 JWT，实时查库判定），置 NULL 即实时回落游客态；
     * 只动这一列 —— 不改 {@code status}、不动 {@code email}（账号标识），登录与已发表内容不受影响。
     * <p>
     * 事务边界：方法入口开事务（含两条写：user 行 + 验证码清理，须同生共死），
     * 锁在事务内获取（与 {@code AccountCloser#close} 同一口径）。
     */
    private void doUnbindEmail(Long id) {
        // 存在性校验（契约 4001「用户不存在」）；读与后续写同处临界区，避免读到的现状事后被推翻
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(4001, "用户不存在");
        }
        String bindEmail = user.getBindEmail();
        if (!StringUtils.hasText(bindEmail)) {
            // 未认证账号无可解绑对象（已注销账号的 bind_email 恒为 NULL，同样命中此处）
            throw new BusinessException("该用户未绑定邮箱");
        }
        // 审计变更前后值：解绑邮箱会让账号从「已认证」回落「游客」，是能力边界的跃迁
        AuditSnapshot.before(user);
        // 必须用 LambdaUpdateWrapper 显式 set NULL：updateById 默认 NOT_NULL 策略对 null 字段不写列
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getBindEmail, null));
        user.setBindEmail(null);
        AuditSnapshot.after(user);
        // 残留验证码清理（与注销同口径）：表无 user_id 列，按 email 匹配删除，
        // 避免残留验证码在他端被消费
        emailVerificationCodeMapper.delete(new LambdaQueryWrapper<EmailVerificationCode>()
                .eq(EmailVerificationCode::getEmail, bindEmail));
        // 邮箱换绑即吊销既有 token：认证态是「绑定邮箱」派生出来的，换绑改变了这个账号的能力边界，
        // 换绑前签发的 token 携带的是旧身份语义 —— 一律作废，由静默登录换发新 token。
        tokenBlacklist.revokeUser(id);
    }

    @Override
    public void kickSessions(Long id) {
        // 「人工踢下线」：不改任何账号列，只让该 userId 的**全部**已签发 token 立即失效。
        // 临界区与启停 / 解绑 / 注销一致 —— 避免与并发的状态写交错出「刚踢又被恢复」的中间态。
        userStateWriteLock.run(id, () -> {
            User current = userMapper.selectById(id);
            if (current == null) {
                throw new BusinessException(4001, "用户不存在");
            }
            // 踢下线不改任何账号列，但它是**影响该用户会话**的动作 ⇒ 记下对象现状便于事后核对对象是否正确
            AuditSnapshot.before(current);
            tokenBlacklist.revokeUser(id);
        });
    }

    @Override
    public void deleteAccount(Long id) {
        // 管理员路径无对方 token 明文（token = null）：匿名化写库、清验证码、通知删除事件、
        // userId 维度拉黑与终态保护整体收敛到 AccountCloser（与本人自注销唯一实现）。
        accountCloser.close(id, null, false);
    }

    // 本域不含垂直越权防护（无 checkAdminOperation / 角色解析）：管理端为单一管理员模型，
    // /admin/** 由 AdminAuthFilter 的管理端 JWT 校验统一保护。

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        // 出参空值口径（管理端列表）：可空字符串列恒非空串 —— avatar 无值 → 空串，端上无需判空
        vo.setAvatar(orEmpty(imageUrlUtil.toAbsoluteUrl(user.getAvatar())));
        vo.setStatus(user.getStatus());
        // 是否绑定微信：由 openid 非空派生（不暴露 openid 明文），管理端用户列表消费
        vo.setWechatBound(user.getOpenid() != null);
        // 空串 = 未认证（管理端认证态唯一判据，与端上同口径）
        vo.setBindEmail(orEmpty(user.getBindEmail()));
        vo.setCreatedAt(user.getCreatedAt());
        // C2：列表统一带 updatedAt（表已有列，无需 DDL）
        vo.setUpdatedAt(user.getUpdatedAt());
        return vo;
    }

    /** 出参空值归一：{@code null} → 空串（管理端列表的字符串列恒非空串，端上无需判空） */
    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 游客默认昵称：食客 + ID 尾 4 位（id 不足 4 位时取全量）。
     * <p>
     * 「游客短标识」不作为任何接口出参：该值是 `id` 的纯派生，
     * 学生端与管理端各自按同一规则现算；此处仅用于**建号默认昵称**这一处服务端写入。
     */
    private String buildGuestNickname(Long userId) {
        String id = String.valueOf(userId);
        String tail = id.length() > 4 ? id.substring(id.length() - 4) : id;
        return "食客" + tail;
    }

    // ==================== 跨域只读契约实现（P0-1：判据唯一真源 = auth） ====================

    /**
     * UGC 准入判定（{@code auth.aspect.RequireVerifiedAspect} 与各写入口共用的唯一判据：
     * 未登录 → 401「请先登录」；账号非 active → 403「账号已被禁用」；未认证 → 4031「请先完成学号邮箱认证」）。
     */
    @Override
    public void requireUgcAuthorized(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "请先登录");
        }
        // JWT 载荷不含 status，禁用/注销账号的存量 token 在有效期内仍可被携带，
        // 这里按 user.status 实时判定，非 active 一律拒绝 UGC 写操作。
        if (!UserConst.STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }
        // 限言：违规累积处置的中间档 —— 写端点一律 403，**读不受限**。
        // 判据是「到期时刻是否在未来」，故到期后无需任何人工动作即自动恢复。
        if (user.getMutedUntil() != null && user.getMutedUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException(403, "账号已被限制发言，请稍后再试");
        }
        if (!AuthStateUtil.isVerified(user.getBindEmail())) {
            // 使用细分的业务码 4031 标识「未认证邮箱」，与普通权限拒绝（code=403）区分，
            // 便于前端对「需先认证」与「无权限」给出不同引导（避免越权错误被误导向邮箱认证）。
            throw new BusinessException(4031, "请先完成学号邮箱认证");
        }
    }

    @Override
    public UserAuthContextVO getAuthContext(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        // 认证态在此折算为布尔值下发：bindEmail 明文不出 auth 域
        return new UserAuthContextVO(user.getId(), AuthStateUtil.isVerified(user.getBindEmail()),
                user.getOpenid());
    }

    @Override
    public boolean isVerifiedById(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userMapper.selectById(userId);
        return user != null && AuthStateUtil.isVerified(user.getBindEmail());
    }

    @Override
    public Map<Long, String> mapNicknameByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, String> map = new HashMap<>();
        userMapper.selectList(new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getNickname)
                        .in(User::getId, userIds))
                .forEach(u -> map.put(u.getId(), u.getNickname()));
        return map;
    }

    @Override
    public Map<Long, UserBriefVO> mapBriefByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, UserBriefVO> map = new HashMap<>();
        userMapper.selectList(new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getNickname, User::getAvatar)
                        .in(User::getId, userIds))
                .forEach(u -> map.put(u.getId(), new UserBriefVO(u.getId(), u.getNickname(),
                        imageUrlUtil.toAbsoluteUrl(u.getAvatar()))));
        return map;
    }
}
