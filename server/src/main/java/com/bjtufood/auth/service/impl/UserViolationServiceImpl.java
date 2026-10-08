package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bjtufood.auth.config.TokenBlacklist;
import com.bjtufood.auth.constant.UserConst;
import com.bjtufood.auth.constant.ViolationConst;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.entity.UserViolation;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.mapper.UserViolationMapper;
import com.bjtufood.auth.service.UserViolationService;
import com.bjtufood.common.alert.AlertType;
import com.bjtufood.common.alert.SecurityAlertNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 违规累积与梯度处置实现。
 *
 * <p><b>阈值</b>：累计违规 {@value #WARN_THRESHOLD} 次警告 → {@value #MUTE_24H_THRESHOLD} 次限言 24 小时
 * → {@value #MUTE_7D_THRESHOLD} 次限言 7 天 → {@value #BAN_THRESHOLD} 次封禁。
 * 取值口径：前两档刻意留出「误触 / 试探」的余量（三到五次，正常用户不会碰到），
 * 上限档对应「持续越线」——到这一步已不是误操作，须立即止损。
 *
 * <p><b>事务口径</b>：{@code REQUIRES_NEW} —— 计数必须**独立提交**。机审命中发生在业务事务
 * 之外（内容被拦、请求整体失败），若并入调用方事务，一笔回滚就会把「对方违规了」这一事实一起抹掉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserViolationServiceImpl implements UserViolationService {

    /** 累计违规达到该次数 ⇒ 账号标记 + 警告 */
    static final int WARN_THRESHOLD = 3;

    /** 累计违规达到该次数 ⇒ 限言 24 小时 */
    static final int MUTE_24H_THRESHOLD = 6;

    /** 累计违规达到该次数 ⇒ 限言 7 天 */
    static final int MUTE_7D_THRESHOLD = 9;

    /** 累计违规达到该次数 ⇒ 封禁 */
    static final int BAN_THRESHOLD = 12;

    private static final long MUTE_24H_HOURS = 24L;

    private static final long MUTE_7D_DAYS = 7L;

    private final UserMapper userMapper;

    private final UserViolationMapper userViolationMapper;

    /**
     * 与启停 / 解绑邮箱 / 注销**共用同一把** userId 锁：违规处置写的是同一行账号状态，
     * 与管理员的手工启停必须互斥，否则并发下会出现「自动封禁 + 人工启用」交错后状态与黑名单不一致。
     */
    private final UserStateWriteLock userStateWriteLock;

    private final TokenBlacklist tokenBlacklist;

    private final SecurityAlertNotifier securityAlertNotifier;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordModerationHit(String openid) {
        if (!StringUtils.hasText(openid)) {
            return;
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getOpenid, openid));
        if (user == null) {
            return;
        }
        record(user, ViolationConst.SOURCE_MODERATION, "机审命中违规内容（已被拦截，未入库）");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordReportUpheld(Long authorId) {
        if (authorId == null) {
            return;
        }
        User user = userMapper.selectById(authorId);
        if (user == null) {
            return;
        }
        record(user, ViolationConst.SOURCE_REPORT, "举报成立（管理端处置结论为通过）");
    }

    /**
     * 记一次违规：累加计数 → 按新计数判定处置 → 落留痕 → 达处置档位即告警。
     */
    private void record(User user, String source, String detail) {
        // 已注销账号不再处置（其内容已匿名化归属，继续升级处置无意义）
        if (UserConst.STATUS_DELETED.equals(user.getStatus())) {
            return;
        }
        int count = (user.getViolationCount() == null ? 0 : user.getViolationCount()) + 1;
        String action = actionFor(count);
        apply(user.getId(), count, action);

        UserViolation row = new UserViolation();
        row.setUserId(user.getId());
        row.setSource(source);
        row.setDetail(detail);
        row.setAction(action);
        userViolationMapper.insert(row);

        if (!ViolationConst.ACTION_COUNTED.equals(action)) {
            // 🔴 每次处置动作必须告警：防「静默封号」—— 学生账号被封必须有人（运维）立刻知道
            securityAlertNotifier.notify(AlertType.VIOLATION_PENALTY, "学生账号违规累积处置",
                    "userId=" + user.getId() + " · 累计违规 " + count + " 次 · 处置=" + action
                            + " · 来源=" + source);
        }
        log.info("[VIOLATION] userId={} count={} action={} source={}", user.getId(), count, action, source);
    }

    /** 按处置动作写入账号列（计数恒写；限言写到期时刻；封禁写 status 并吊销 token） */
    private void apply(Long userId, int count, String action) {
        userStateWriteLock.run(userId, () -> {
            LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
            update.set(User::getViolationCount, count);
            switch (action) {
                case ViolationConst.ACTION_MUTE_24H ->
                        update.set(User::getMutedUntil, LocalDateTime.now().plusHours(MUTE_24H_HOURS));
                case ViolationConst.ACTION_MUTE_7D ->
                        update.set(User::getMutedUntil, LocalDateTime.now().plusDays(MUTE_7D_DAYS));
                case ViolationConst.ACTION_BAN -> {
                    update.set(User::getStatus, UserConst.STATUS_DISABLED);
                    // 封禁即解除限言计时：终态是「禁用」，再挂一个到期时刻只会让状态有两个来源
                    update.set(User::getMutedUntil, null);
                }
                default -> {
                    // counted / warn：只累加计数，不改状态与限言
                }
            }
            userMapper.update(null, update);
            if (ViolationConst.ACTION_BAN.equals(action)) {
                // 封禁 = 等同账号禁用：存量 token 必须立即失效，不能等自然过期
                tokenBlacklist.revokeUser(userId);
            }
        });
    }

    /**
     * 按累计次数判定处置档位。
     *
     * @param count 累计违规次数（含本次）
     * @return 处置动作（{@link ViolationConst}）
     */
    static String actionFor(int count) {
        if (count >= BAN_THRESHOLD) {
            return ViolationConst.ACTION_BAN;
        }
        if (count >= MUTE_7D_THRESHOLD) {
            return ViolationConst.ACTION_MUTE_7D;
        }
        if (count >= MUTE_24H_THRESHOLD) {
            return ViolationConst.ACTION_MUTE_24H;
        }
        if (count >= WARN_THRESHOLD) {
            return ViolationConst.ACTION_WARN;
        }
        return ViolationConst.ACTION_COUNTED;
    }
}
