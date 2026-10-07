package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.auth.config.TokenBlacklist;
import com.bjtufood.auth.constant.UserConst;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.dto.UserBriefVO;
import com.bjtufood.auth.entity.EmailVerificationCode;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.event.UserAccountClosedEvent;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link UserServiceImpl} 单元测试。
 * <p>
 * 聚焦<b>UGC 准入判据（{@code requireUgcAuthorized}）</b>与<b>跨域只读契约</b>——
 * 前者是全站唯一的 UGC 写门（{@code @RequireVerified} 切面、评价提交、反馈写入都经它），
 * 错误码语义（401 / 403 / 4031）直接决定端上引导，锁死价值最高；后者是 P0-1 跨域解耦的落点。
 * 另含<b>管理端状态写（{@code updateStatus}）</b>的两条高风险口径：列级更新（防与本人资料更新
 * 互相覆盖）与「DB 写 + 黑名单写」同临界区（防终态 status=active 却被拉黑、用户永久 401）。
 * <p>
 * 不做真库集成：Mapper 全部打桩，被测类是纯 POJO（无 Spring 代理，{@code @Transactional} 不生效也无需生效）。
 */
class UserServiceImplTest {

    /**
     * 初始化 MyBatis-Plus 的 lambda 缓存（{@code TableInfo}）。
     * <p>
     * <b>为何需要</b>：本类测试会触发 {@code new LambdaQueryWrapper<User>().select(User::getId, ...)}，
     * 而 MyBatis-Plus 的 {@code LambdaQueryWrapper} 依赖 {@code TableInfoHelper} 里的实体元数据
     * （表名、列名与 getter 的映射）来把 {@code User::getId} 翻译成 SQL 片段。该缓存
     * <b>正常由 Spring 启动时的 Mapper 扫描填充</b>，纯 Mockito 单测（无 Spring 上下文）下为空，
     * 直接构造 wrapper 会抛 {@code "MybatisPlus can not find lambda cache for this entity"}。
     * <p>
     * 这里显式初始化，是让「构造 wrapper」这一被测逻辑得以在单测中被真实执行，
     * <b>不是</b>为了让测试通过而绕开被测行为（若跳过 wrapper 构造，跨域契约的查询条件
     * 就完全不受测试保护了）。
     */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), UserServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, User.class);
    }

    private final UserMapper userMapper = mock(UserMapper.class);
    private final EmailVerificationCodeMapper codeMapper = mock(EmailVerificationCodeMapper.class);
    private final ImageUrlUtil imageUrlUtil = mock(ImageUrlUtil.class);
    private final TokenBlacklist tokenBlacklist = mock(TokenBlacklist.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    /**
     * 真实实例（非 mock）：{@code updateStatus} 的并发语义就是本组件的临界区语义，
     * 打桩会让「禁用 + 启用交错」的用例失去意义。状态写用例各自另建实例，避免用例间共享锁状态。
     */
    private final UserStateWriteLock userStateWriteLock = new UserStateWriteLock();
    /**
     * 真实实现（非 mock）包裹 mock mapper：管理端 {@code deleteAccount} 的行为断言
     * （匿名化写库 / 发事件 / 拉黑）都落在 AccountCloser 的真实执行路径上。
     */
    private final AccountCloser accountCloser =
            new AccountCloser(userMapper, codeMapper, eventPublisher, tokenBlacklist, userStateWriteLock);

    private UserServiceImpl service() {
        return new UserServiceImpl(userMapper, codeMapper, imageUrlUtil, tokenBlacklist,
                userStateWriteLock, accountCloser);
    }

    private static User user(Long id, String status, String bindEmail) {
        User u = new User();
        u.setId(id);
        u.setStatus(status);
        u.setBindEmail(bindEmail);
        return u;
    }

    // ==================== requireUgcAuthorized：UGC 写门 ====================

    @Test
    @DisplayName("userId 为 null → 401「请先登录」（未登录不得触发认证提示）")
    void nullUserIdIsUnauthorized() {
        assertThatThrownBy(() -> service().requireUgcAuthorized(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(401);
                    assertThat(be.getMessage()).isEqualTo("请先登录");
                });
        verify(userMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("用户不存在 → 401「请先登录」（不得泄露「账号不存在」）")
    void missingUserIsUnauthorized() {
        when(userMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service().requireUgcAuthorized(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(401));
    }

    @ParameterizedTest(name = "status={0}")
    @NullSource
    @ValueSource(strings = {"disabled", "deleted", "ACTIVE", ""})
    @DisplayName("非 active 账号（含大小写与空白变体）→ 403：JWT 不含 status，须实时判定")
    void nonActiveStatusIsForbidden(String status) {
        // 口径为 "active".equals(status)，大小写敏感；本用例锁定「非精确 active 一律拒绝」
        when(userMapper.selectById(1L)).thenReturn(user(1L, status, "a@bjtu.edu.cn"));
        assertThatThrownBy(() -> service().requireUgcAuthorized(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(403);
                    assertThat(be.getMessage()).isEqualTo("账号已被禁用");
                });
    }

    @ParameterizedTest(name = "bindEmail={0}")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("bindEmail 为空/空白（未完成邮箱认证）→ 4031，与普通 403 区分")
    void unverifiedUserGets4031(String bindEmail) {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "active", bindEmail));
        assertThatThrownBy(() -> service().requireUgcAuthorized(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode())
                            .as("未认证须用 4031 细分码，避免端上把「需先认证」误导向「无权限」")
                            .isEqualTo(4031);
                    assertThat(be.getMessage()).isEqualTo("请先完成学号邮箱认证");
                });
    }

    @Test
    @DisplayName("active + 已绑定校园邮箱 → 放行")
    void verifiedActiveUserPasses() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "active", "20240001@bjtu.edu.cn"));
        assertThatCode(() -> service().requireUgcAuthorized(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("判定顺序：非 active 且未认证 → 先报 403（禁用优先于未认证）")
    void disabledTakesPrecedenceOverUnverified() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "disabled", null));
        assertThatThrownBy(() -> service().requireUgcAuthorized(1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(403));
    }

    // ==================== getAuthContext / isVerifiedById ====================

    @Test
    @DisplayName("getAuthContext：bindEmail 明文不出 auth 域，仅下发折算后的布尔认证态")
    void authContextDoesNotLeakBindEmail() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "active", "20240001@bjtu.edu.cn"));

        UserAuthContextVO ctx = service().getAuthContext(1L);

        assertThat(ctx).isNotNull();
        assertThat(ctx.getUserId()).isEqualTo(1L);
        assertThat(ctx.isVerified()).isTrue();
        // 契约上无 bindEmail 出参：确认 DTO 确实不承载明文邮箱
        assertThat(UserAuthContextVO.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .noneMatch(n -> n.toLowerCase().contains("email"));
    }

    @Test
    @DisplayName("getAuthContext：userId 为 null 或用户不存在 → 返回 null（不抛）")
    void authContextReturnsNullOnMissing() {
        when(userMapper.selectById(9L)).thenReturn(null);
        UserServiceImpl svc = service();
        assertThat(svc.getAuthContext(null)).isNull();
        assertThat(svc.getAuthContext(9L)).isNull();
    }

    @Test
    @DisplayName("isVerifiedById：null / 不存在 / 未认证 / 已认证 四种路径")
    void isVerifiedByIdPaths() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "active", "a@bjtu.edu.cn"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, "active", "  "));
        when(userMapper.selectById(9L)).thenReturn(null);
        UserServiceImpl svc = service();
        assertThat(svc.isVerifiedById(null)).isFalse();
        assertThat(svc.isVerifiedById(9L)).isFalse();
        assertThat(svc.isVerifiedById(2L)).isFalse();
        assertThat(svc.isVerifiedById(1L)).isTrue();
    }

    // ==================== 跨域只读契约 ====================

    @Test
    @DisplayName("mapBriefByIds：avatar 经 ImageUrlUtil 转绝对地址（跨域不外泄相对路径）")
    void mapBriefByIdsConvertsAvatarToAbsoluteUrl() {
        User u1 = user(1L, "active", null);
        u1.setNickname("小明");
        u1.setAvatar("/images/a.jpg");
        User u2 = user(2L, "active", null);
        u2.setNickname("小红");
        when(userMapper.selectList(any())).thenReturn(Arrays.asList(u1, u2));
        when(imageUrlUtil.toAbsoluteUrl("/images/a.jpg")).thenReturn("http://host/api/v1/images/a.jpg");

        Map<Long, UserBriefVO> map = service().mapBriefByIds(List.of(1L, 2L));

        assertThat(map).containsOnlyKeys(1L, 2L);
        assertThat(map.get(1L).getNickname()).isEqualTo("小明");
        assertThat(map.get(1L).getAvatarUrl()).isEqualTo("http://host/api/v1/images/a.jpg");
        // avatar 为 null 的行：实现对每行都调 toAbsoluteUrl（不做 null 短路），
        // 故此处断言其结果为 null，即 ImageUrlUtil 自身须对 null 安全
        assertThat(map.get(2L).getAvatarUrl()).isNull();
    }

    @Test
    @DisplayName("mapBriefByIds / mapNicknameByIds：入参 null 或空集合 → 空 Map 且不查库")
    void mapContractsShortCircuitOnEmptyInput() {
        UserServiceImpl svc = service();
        assertThat(svc.mapBriefByIds((Collection<Long>) null)).isEmpty();
        assertThat(svc.mapBriefByIds(List.of())).isEmpty();
        assertThat(svc.mapNicknameByIds((Collection<Long>) null)).isEmpty();
        assertThat(svc.mapNicknameByIds(List.of())).isEmpty();
        verify(userMapper, never()).selectList(any());
    }

    // ==================== updateStatus：列级更新 + 「DB 写 / 黑名单写」同临界区 ====================

    @Test
    @DisplayName("updateStatus：只写 status 一列，不整行回写")
    void updateStatusWritesStatusColumnOnly() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, UserConst.STATUS_ACTIVE, "a@bjtu.edu.cn"));
        AtomicReference<Wrapper<User>> captured = new AtomicReference<>();
        when(userMapper.update(isNull(), any())).thenAnswer(inv -> {
            captured.set(inv.getArgument(1));
            return 1;
        });

        service().updateStatus(1L, UserConst.STATUS_DISABLED);

        assertThat(captured.get())
                .as("状态写必须是列级更新（update(entity=null, LambdaUpdateWrapper)）：整行实体回写会把各非空列一并写回")
                .isInstanceOf(LambdaUpdateWrapper.class);
        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) captured.get();
        assertThat(wrapper.getSqlSet())
                .as("SET 片段 = status。updated_at 由 DB 时钟维护"
                        + "（docs/schema/README.md §时间戳写入来源），应用层不写该列")
                .contains("status=")
                .doesNotContain("updated_at=");
        assertThat(wrapper.getSqlSet())
                .as("整行回写会连带写回昵称/头像等列，与本人 PUT /auth/profile 的局部更新并发时互相覆盖")
                .doesNotContain("nickname")
                .doesNotContain("avatar")
                .doesNotContain("bind_email")
                .doesNotContain("openid")
                .doesNotContain("username");
        assertThat(wrapper.getParamNameValuePairs().values())
                .as("status 新值必须作为参数落库")
                .contains(UserConst.STATUS_DISABLED);
        verify(tokenBlacklist).revokeUser(1L);
    }

    @Test
    @DisplayName("并发「禁用 + 启用」交错：终态不得出现 status=active 且该 userId 仍在拉黑中")
    void interleavedDisableAndEnableNeverLeavesActiveButRevoked() throws Exception {
        UserMapper mapper = mock(UserMapper.class);
        // 真实现（非 mock）：断言对象就是最终拉黑状态
        TokenBlacklist blacklist = new TokenBlacklist();
        UserStateWriteLock lock = new UserStateWriteLock();
        UserServiceImpl svc = new UserServiceImpl(mapper, mock(EmailVerificationCodeMapper.class),
                imageUrlUtil, blacklist, lock,
                new AccountCloser(mapper, mock(EmailVerificationCodeMapper.class),
                        mock(ApplicationEventPublisher.class), blacklist, lock));

        // 假 user 行（替代真库，使交错可被确定性构造）
        AtomicReference<String> dbStatus = new AtomicReference<>(UserConst.STATUS_ACTIVE);
        // 禁用方「已写库、尚未写黑名单」——即「DB 写」与「黑名单写」之间的非原子窗口
        CountDownLatch disabledWritten = new CountDownLatch(1);
        // 启用方整段跑完（含 restoreUser）后才计数
        CountDownLatch enabledFinished = new CountDownLatch(1);

        when(mapper.selectById(1L)).thenAnswer(inv -> user(1L, dbStatus.get(), "a@bjtu.edu.cn"));
        when(mapper.update(isNull(), any()))
                // 第 1 次调用 = 禁用方写库：停在「写库完成 → 写黑名单」之间，把该窗口交给启用方
                .thenAnswer(inv -> {
                    dbStatus.set(UserConst.STATUS_DISABLED);
                    disabledWritten.countDown();
                    // 上界等待：启用方被挡在同一临界区外时此处必然等到超时（预期路径）；
                    // 未共用临界区时由启用方整段跑完提前放行，最终状态随即被断言检出
                    enabledFinished.await(200, TimeUnit.MILLISECONDS);
                    return 1;
                })
                // 第 2 次调用 = 启用方写库（仅在禁用方让出临界区后到达）
                .thenAnswer(inv -> {
                    dbStatus.set(UserConst.STATUS_ACTIVE);
                    return 1;
                });

        AtomicReference<Throwable> disableFailure = new AtomicReference<>();
        Thread disable = new Thread(() -> {
            try {
                svc.updateStatus(1L, UserConst.STATUS_DISABLED);
            } catch (Throwable t) {
                disableFailure.set(t);
            }
        });
        disable.start();
        assertThat(disabledWritten.await(2, TimeUnit.SECONDS)).isTrue();

        AtomicReference<Throwable> enableFailure = new AtomicReference<>();
        Thread enable = new Thread(() -> {
            try {
                svc.updateStatus(1L, UserConst.STATUS_ACTIVE);
            } catch (Throwable t) {
                enableFailure.set(t);
            } finally {
                enabledFinished.countDown();
            }
        });
        enable.start();

        disable.join(2000);
        enable.join(2000);
        assertThat(disable.isAlive()).as("禁用线程必须在 2s 内结束（不得死锁）").isFalse();
        assertThat(enable.isAlive()).as("启用线程必须在 2s 内结束（不得死锁）").isFalse();
        assertThat(disableFailure.get()).isNull();
        assertThat(enableFailure.get()).isNull();

        // 终态自洽：active 必须同时「未被拉黑」，否则用户连重新登录换到的新 token 也一律 401
        assertThat(dbStatus.get()).isEqualTo(UserConst.STATUS_ACTIVE);
        assertThat(blacklist.isUserRevoked(1L))
                .as("终态 DB=active 时该 userId 不得仍在拉黑中（若「DB 写 + 黑名单写」未同临界区，此处会为 true）")
                .isFalse();
    }

    // ==================== unbindEmail：管理端解绑认证邮箱 ====================

    @Test
    @DisplayName("unbindEmail：用户不存在 → 4001「用户不存在」，零副作用")
    void unbindEmailRejectsMissingUser() {
        when(userMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service().unbindEmail(9L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));

        verify(userMapper, never()).update(any(), any());
        verifyNoInteractions(codeMapper);
    }

    @Test
    @DisplayName("unbindEmail：目标未绑定邮箱（bind_email 为空，含已注销账号）→ 400，不写库")
    void unbindEmailRejectsUnboundUser() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, UserConst.STATUS_ACTIVE, null));

        assertThatThrownBy(() -> service().unbindEmail(1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessage())
                        .isEqualTo("该用户未绑定邮箱"));

        verify(userMapper, never()).update(any(), any());
        verifyNoInteractions(codeMapper);
    }

    @Test
    @DisplayName("unbindEmail：正常解绑 → 只置空 bind_email 一列（不改 status / username / openid），并清理该邮箱验证码")
    void unbindEmailClearsBindEmailColumnOnly() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, UserConst.STATUS_ACTIVE, "20240001@bjtu.edu.cn"));
        AtomicReference<Wrapper<User>> captured = new AtomicReference<>();
        when(userMapper.update(isNull(), any())).thenAnswer(inv -> {
            captured.set(inv.getArgument(1));
            return 1;
        });

        service().unbindEmail(1L);

        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) captured.get();
        assertThat(wrapper.getSqlSet())
                .as("解绑必须是列级更新且只动 bind_email：认证态判据唯一，其余列（status / 账号标识 / 微信绑定）不属于解绑语义")
                .contains("bind_email=")
                .doesNotContain("status=")
                .doesNotContain("username")
                .doesNotContain("openid")
                .doesNotContain("nickname");
        assertThat(wrapper.getParamNameValuePairs().values())
                .as("SET 片段只含 NULL（bind_email 置空），不带其它新值参数")
                .containsNull();
        // 残留验证码清理：表无 user_id 列，按绑定邮箱匹配删除（与注销同口径）
        verify(codeMapper).delete(any());
    }

    // ==================== deleteAccount：管理端删除账号（代注销） ====================

    @Test
    @DisplayName("deleteAccount：用户不存在 → 4001「用户不存在」（区别于本人自注销的 401 不泄露口径）")
    void adminDeleteAccountRejectsMissingUser() {
        when(userMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service().deleteAccount(9L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));

        verify(userMapper, never()).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @ParameterizedTest(name = "status={0}")
    @ValueSource(strings = {"deleted", "disabled"})
    @DisplayName("deleteAccount：终态保护 —— 已注销 / 已禁用账号拒绝注销（与本人口径一致），不写库")
    void adminDeleteAccountRejectsTerminalStates(String status) {
        when(userMapper.selectById(1L)).thenReturn(user(1L, status, "a@bjtu.edu.cn"));

        assertThatThrownBy(() -> service().deleteAccount(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessage())
                        .isEqualTo("deleted".equals(status) ? "账号已注销" : "账号已被禁用，无法注销"));

        verify(userMapper, never()).update(any(), any());
        verify(tokenBlacklist, never()).revokeUser(1L);
    }

    @Test
    @DisplayName("deleteAccount：正常注销 → 匿名化写库 + 发 UserAccountClosedEvent + userId 维度拉黑（无 token 明文可拉黑）")
    void adminDeleteAccountAnonymizesPublishesEventAndBlacklists() {
        User u = user(1L, UserConst.STATUS_ACTIVE, "a@bjtu.edu.cn");
        u.setEmail("legacy@bjtu.edu.cn");
        when(userMapper.selectById(1L)).thenReturn(u);

        service().deleteAccount(1L);

        verify(userMapper).update(any(), any());
        // UserAccountClosedEvent 是单参 record（不继承 ApplicationEvent）→ captor 必须用 Object 重载
        ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(evt.capture());
        assertThat(evt.getValue()).isInstanceOf(UserAccountClosedEvent.class);
        // 管理端拿不到对方 token：仅 userId 维度拉黑（token 维度入参为 null，由黑名单自身安全跳过）
        verify(tokenBlacklist).revokeUser(1L);
        verify(tokenBlacklist, never()).revoke(anyString());
    }
}
