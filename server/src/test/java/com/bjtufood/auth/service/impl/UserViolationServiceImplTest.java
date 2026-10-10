package com.bjtufood.auth.service.impl;

import com.bjtufood.auth.config.TokenBlacklist;
import com.bjtufood.auth.constant.UserConst;
import com.bjtufood.auth.constant.ViolationConst;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.common.alert.AlertType;
import com.bjtufood.common.alert.SecurityAlertNotifier;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 违规累积梯度处置回归。
 *
 * <p>锁定三条硬约束：① 档位判定（次数 → 处置）不得漂移；② **每次处置动作必须告警**（防「静默封号」）；
 * ③ 计数与处置动作直接 upsert 到 user 行（{@code violation_count} / {@code muted_until} / {@code status}），
 * 不落任何留痕表。
 */
class UserViolationServiceImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserStateWriteLock writeLock = new UserStateWriteLock();
    private final TokenBlacklist tokenBlacklist = mock(TokenBlacklist.class);
    private final SecurityAlertNotifier notifier = mock(SecurityAlertNotifier.class);

    private final UserViolationServiceImpl service = new UserViolationServiceImpl(
            userMapper, writeLock, tokenBlacklist, notifier);

    /** 纯 Mockito 无 Spring 上下文，MyBatis-Plus 的 lambda 缓存需显式初始化 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), UserViolationServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, User.class);
    }

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
    @DisplayName("未达处置阈值：只累加 user.violation_count，不告警、不动账号状态")
    void belowThresholdOnlyCounts() {
        when(userMapper.selectOne(any())).thenReturn(user(1));

        service.recordModerationHit("openid-9");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<User>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
        // 写面恰为累计计数列：不含 status / muted_until
        assertThat(captor.getValue().getSqlSet()).contains("violation_count");
        assertThat(captor.getValue().getSqlSet()).doesNotContain("status").doesNotContain("muted_until");
        verify(notifier, never()).notify(any(), any(), any());
        verify(tokenBlacklist, never()).revokeUser(any());
    }

    @Test
    @DisplayName("达限言档：写 muted_until + 推告警（处置不得静默），不吊销 token（读仍可用）")
    void muteThresholdUpdatesMuteAndAlerts() {
        when(userMapper.selectById(9L)).thenReturn(user(5));

        service.recordReportUpheld(9L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<User>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("violation_count").contains("muted_until");
        verify(notifier).notify(eq(AlertType.VIOLATION_PENALTY), eq("学生账号违规累积处置"),
                any(String.class));
        verify(tokenBlacklist, never()).revokeUser(any());
    }

    @Test
    @DisplayName("达封禁档：写 status 并吊销该用户全部 token（存量凭证立即失效）")
    void banThresholdBansAndRevokesTokens() {
        when(userMapper.selectById(9L)).thenReturn(user(11));

        service.recordReportUpheld(9L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<User>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("violation_count").contains("status");
        verify(tokenBlacklist).revokeUser(9L);
        verify(notifier).notify(eq(AlertType.VIOLATION_PENALTY), any(), any());
    }

    @Test
    @DisplayName("已注销账号不再累积处置（内容已匿名化归属，继续升级无意义）")
    void deletedUserIsIgnored() {
        User deleted = user(5);
        deleted.setStatus(UserConst.STATUS_DELETED);
        when(userMapper.selectById(9L)).thenReturn(deleted);

        service.recordReportUpheld(9L);

        verify(userMapper, never()).update(any(), any());
        verify(notifier, never()).notify(any(), any(), any());
    }

    @Test
    @DisplayName("openid 为空（历史学号账号）或作者缺失：跳过计数，不抛异常")
    void unresolvedIdentityIsSkipped() {
        service.recordModerationHit(null);
        service.recordReportUpheld(null);

        verify(userMapper, never()).update(any(), any());
        verify(userMapper, never()).selectById(any());
    }
}
