package com.bjtufood.auth.service.impl;

import com.bjtufood.auth.config.TokenBlacklist;
import com.bjtufood.auth.constant.UserConst;
import com.bjtufood.auth.constant.ViolationConst;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.entity.UserViolation;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.mapper.UserViolationMapper;
import com.bjtufood.common.alert.AlertType;
import com.bjtufood.common.alert.SecurityAlertNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 违规累积梯度处置回归。
 *
 * <p>锁定两条硬约束：① 档位判定（次数 → 处置）不得漂移；② **每次处置动作必须留痕 + 告警**
 * （防「静默封号」）。
 */
class UserViolationServiceImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserViolationMapper userViolationMapper = mock(UserViolationMapper.class);
    private final UserStateWriteLock writeLock = new UserStateWriteLock();
    private final TokenBlacklist tokenBlacklist = mock(TokenBlacklist.class);
    private final SecurityAlertNotifier notifier = mock(SecurityAlertNotifier.class);

    private final UserViolationServiceImpl service = new UserViolationServiceImpl(
            userMapper, userViolationMapper, writeLock, tokenBlacklist, notifier);

    private User user(int violationCount) {
        User user = new User();
        user.setId(9L);
        user.setOpenid("openid-9");
        user.setStatus(UserConst.STATUS_ACTIVE);
        user.setViolationCount(violationCount);
        return user;
    }

    @Test
    @DisplayName("档位判定：3 警告 / 6 限言 24h / 9 限言 7d / 12 封禁，阈值以下仅累计")
    void actionLadderIsStable() {
        assertThat(UserViolationServiceImpl.actionFor(1)).isEqualTo(ViolationConst.ACTION_COUNTED);
        assertThat(UserViolationServiceImpl.actionFor(2)).isEqualTo(ViolationConst.ACTION_COUNTED);
        assertThat(UserViolationServiceImpl.actionFor(3)).isEqualTo(ViolationConst.ACTION_WARN);
        assertThat(UserViolationServiceImpl.actionFor(5)).isEqualTo(ViolationConst.ACTION_WARN);
        assertThat(UserViolationServiceImpl.actionFor(6)).isEqualTo(ViolationConst.ACTION_MUTE_24H);
        assertThat(UserViolationServiceImpl.actionFor(9)).isEqualTo(ViolationConst.ACTION_MUTE_7D);
        assertThat(UserViolationServiceImpl.actionFor(12)).isEqualTo(ViolationConst.ACTION_BAN);
        assertThat(UserViolationServiceImpl.actionFor(100)).isEqualTo(ViolationConst.ACTION_BAN);
    }

    @Test
    @DisplayName("未达处置阈值：只累计计数 + 留痕，不告警、不动账号状态")
    void belowThresholdOnlyCounts() {
        when(userMapper.selectOne(any())).thenReturn(user(1));

        service.recordModerationHit("openid-9");

        ArgumentCaptor<UserViolation> row = ArgumentCaptor.forClass(UserViolation.class);
        verify(userViolationMapper).insert(row.capture());
        assertThat(row.getValue().getAction()).isEqualTo(ViolationConst.ACTION_COUNTED);
        assertThat(row.getValue().getSource()).isEqualTo(ViolationConst.SOURCE_MODERATION);
        verify(notifier, never()).notify(any(), any(), any());
        verify(userMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("达限言档：写留痕 + 推告警（处置不得静默）")
    void muteThresholdLeavesTraceAndAlerts() {
        when(userMapper.selectById(9L)).thenReturn(user(5));

        service.recordReportUpheld(9L);

        ArgumentCaptor<UserViolation> row = ArgumentCaptor.forClass(UserViolation.class);
        verify(userViolationMapper).insert(row.capture());
        assertThat(row.getValue().getAction()).isEqualTo(ViolationConst.ACTION_MUTE_24H);
        assertThat(row.getValue().getSource()).isEqualTo(ViolationConst.SOURCE_REPORT);
        verify(notifier).notify(eq(AlertType.VIOLATION_PENALTY), eq("学生账号违规累积处置"),
                any(String.class));
        // 限言不解绑 token（读仍要可用），故不吊销
        verify(tokenBlacklist, never()).revokeUser(any());
    }

    @Test
    @DisplayName("达封禁档：吊销该用户全部 token（存量凭证立即失效）")
    void banThresholdRevokesTokens() {
        when(userMapper.selectById(9L)).thenReturn(user(11));

        service.recordReportUpheld(9L);

        ArgumentCaptor<UserViolation> row = ArgumentCaptor.forClass(UserViolation.class);
        verify(userViolationMapper).insert(row.capture());
        assertThat(row.getValue().getAction()).isEqualTo(ViolationConst.ACTION_BAN);
        verify(tokenBlacklist).revokeUser(9L);
    }

    @Test
    @DisplayName("已注销账号不再累积处置（内容已匿名化归属，继续升级无意义）")
    void deletedUserIsIgnored() {
        User deleted = user(5);
        deleted.setStatus(UserConst.STATUS_DELETED);
        when(userMapper.selectById(9L)).thenReturn(deleted);

        service.recordReportUpheld(9L);

        verify(userViolationMapper, never()).insert(any(UserViolation.class));
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("openid 为空（历史学号账号）或作者缺失：跳过计数，不抛异常")
    void unresolvedIdentityIsSkipped() {
        service.recordModerationHit(null);
        service.recordReportUpheld(null);

        verify(userViolationMapper, never()).insert(any(UserViolation.class));
    }
}
