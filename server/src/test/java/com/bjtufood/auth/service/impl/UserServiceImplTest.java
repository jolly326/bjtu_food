package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.auth.config.TokenBlacklist;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.dto.UserBriefVO;
import com.bjtufood.auth.entity.User;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserServiceImpl} 单元测试。
 * <p>
 * 聚焦<b>UGC 准入判据（{@code requireUgcAuthorized}）</b>与<b>跨域只读契约</b>——
 * 前者是全站唯一的 UGC 写门（{@code @RequireVerified} 切面、评价提交、反馈写入都经它），
 * 错误码语义（401 / 403 / 4031）直接决定端上引导，锁死价值最高；后者是 P0-1 跨域解耦的落点。
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
    private final ImageUrlUtil imageUrlUtil = mock(ImageUrlUtil.class);
    private final TokenBlacklist tokenBlacklist = mock(TokenBlacklist.class);

    private UserServiceImpl service() {
        return new UserServiceImpl(userMapper, imageUrlUtil, tokenBlacklist);
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
}
