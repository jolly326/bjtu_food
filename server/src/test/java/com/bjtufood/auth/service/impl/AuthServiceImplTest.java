package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.auth.config.TokenBlacklist;
import com.bjtufood.auth.dto.ProfileUpdateReq;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.event.UserAccountClosedEvent;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.service.EmailCodeService;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.auth.support.JwtUtil;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
import com.bjtufood.wechat.service.WechatService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthServiceImpl} 单元测试。
 * <p>
 * 聚焦认证域高风险逻辑：终态保护（已注销/已禁用账号必须被拒，否则黑名单重启清空后留越权窗口）、
 * 部分更新防 lost update、注销的连带清理（发事件让 notify 域硬删站内消息 + 双维度拉黑 token）。
 * 被测类为纯 POJO：{@code @Transactional} 依赖 Spring 代理，单测中不生效。
 */
class AuthServiceImplTest {

    @BeforeAll
    static void initMybatisLambdaCache() {
        // 纯 Mockito 单测无 Spring 上下文，MyBatis-Plus 的 TableInfo 缓存为空，
        // 构造 LambdaUpdateWrapper 会抛 "can not find lambda cache"。
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), AuthServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, User.class);
    }

    private final UserService userService = mock(UserService.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final EmailVerificationCodeMapper codeMapper = mock(EmailVerificationCodeMapper.class);
    private final EmailCodeService emailCodeService = mock(EmailCodeService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final WechatService wechatService = mock(WechatService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ImageUrlUtil imageUrlUtil = mock(ImageUrlUtil.class);
    private final LocalSensitiveFilter localSensitiveFilter = mock(LocalSensitiveFilter.class);
    private final ContentSecurityService contentSecurityService = mock(ContentSecurityService.class);
    private final TokenBlacklist tokenBlacklist = mock(TokenBlacklist.class);

    /** 构造器参数顺序须与 {@code AuthServiceImpl} 的 final 字段声明顺序逐字一致 */
    private AuthServiceImpl service() {
        // 落库 Bean 用**真实实现**包裹 mock 的 mapper：事务边界收窄（机审移出事务）后，
        // 本类断言仍原样落在 userMapper.update 上 —— 即「可见行为未变」的直接证据。
        return new AuthServiceImpl(userService, userMapper, new AuthProfilePersister(userMapper), codeMapper,
                emailCodeService, passwordEncoder, jwtUtil, wechatService, eventPublisher, imageUrlUtil,
                localSensitiveFilter, contentSecurityService, tokenBlacklist);
    }

    private static User user(Long id, String status) {
        User u = new User();
        u.setId(id);
        u.setStatus(status);
        u.setNickname("小明");
        return u;
    }

    // ==================== verifyEmail：前置门禁 ====================

    @Test
    @DisplayName("verifyEmail：userId 为 null → 401，且不查库")
    void verifyEmailRejectsAnonymous() {
        assertThatThrownBy(() -> service().verifyEmail("123456", null))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(401));
        verify(userMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("verifyEmail：用户不存在 → 异常，且不得写库（不得凭空创建认证态）")
    void verifyEmailRejectsMissingUser() {
        when(userMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service().verifyEmail("123456", 1L))
                .isInstanceOf(BusinessException.class);
        verify(userMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("verifyEmail：disabled/deleted 账号 → 拒绝认证（终态不得被重新激活）")
    void verifyEmailRejectsAbnormalStatus() {
        for (String status : new String[]{"disabled", "deleted"}) {
            when(userMapper.selectById(1L)).thenReturn(user(1L, status));
            assertThatThrownBy(() -> service().verifyEmail("123456", 1L))
                    .isInstanceOf(BusinessException.class);
        }
        verify(userMapper, never()).updateById(any());
    }

    // ==================== deleteAccount：终态保护 ====================

    @Test
    @DisplayName("deleteAccount：userId 为 null / 用户不存在 → 401，且零副作用")
    void deleteAccountRejectsInvalidIdentity() {
        when(userMapper.selectById(9L)).thenReturn(null);
        AuthServiceImpl svc = service();

        assertThatThrownBy(() -> svc.deleteAccount(null, "tk"))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(401));
        assertThatThrownBy(() -> svc.deleteAccount(9L, "tk"))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(401));

        verify(userMapper, never()).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("deleteAccount：已注销账号重复调用 → 400「账号已注销」（黑名单重启后的兜底）")
    void deleteAccountRejectsAlreadyDeleted() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "deleted"));

        assertThatThrownBy(() -> service().deleteAccount(1L, "tk"))
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessage()).isEqualTo("账号已注销"));

        verify(userMapper, never()).update(any(), any());
        verify(tokenBlacklist, never()).revoke(anyString());
    }

    @Test
    @DisplayName("deleteAccount：已禁用账号 → 400「账号已被禁用，无法注销」，不得匿名化")
    void deleteAccountRejectsDisabled() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "disabled"));

        assertThatThrownBy(() -> service().deleteAccount(1L, "tk"))
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessage())
                        .isEqualTo("账号已被禁用，无法注销"));
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("deleteAccount：正常注销 → 匿名化写库 + 发 UserAccountClosedEvent + 双维度拉黑 token")
    void deleteAccountAnonymizesPublishesEventAndBlacklists() {
        User u = user(1L, "active");
        u.setEmail("a@bjtu.edu.cn");
        u.setBindEmail("b@bjtu.edu.cn");
        when(userMapper.selectById(1L)).thenReturn(u);

        service().deleteAccount(1L, "tk-abc");

        verify(userMapper).update(any(), any());
        // 注意 captor 类型：UserAccountClosedEvent 是单参 record（userId），
        // **不继承** ApplicationEvent（不同于 review 的 ReviewSubmittedEvent(source, ...)），
        // 故此处 publishEvent 绑定的是 (Object) 重载，captor 必须是 Object。
        // 用 ApplicationEvent 会因重载不匹配而验证不到。
        ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(evt.capture());
        assertThat(evt.getValue()).isInstanceOf(UserAccountClosedEvent.class);
        verify(tokenBlacklist).revoke("tk-abc");
        verify(tokenBlacklist).revokeUser(1L);
    }

    // ==================== updateProfile：部分更新防 lost update ====================

    @Test
    @DisplayName("updateProfile：昵称与头像都为空 → 拒绝（防无意义写库）")
    void updateProfileRejectsEmptyPayload() {
        assertThatThrownBy(() -> service().updateProfile(1L, new ProfileUpdateReq()))
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessage())
                        .isEqualTo("昵称和头像至少填写一项"));
        verify(userMapper, never()).update(any());
    }

    @Test
    @DisplayName("updateProfile：昵称命中敏感词 → 拒绝且不写库（须在内容安全检测之前拦下）")
    void updateProfileRejectsSensitiveNickname() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "active"));
        when(localSensitiveFilter.containsSensitive("违禁词")).thenReturn(true);
        ProfileUpdateReq req = new ProfileUpdateReq();
        req.setNickname("违禁词");

        assertThatThrownBy(() -> service().updateProfile(1L, req))
                .isInstanceOf(BusinessException.class);
        verify(userMapper, never()).update(any());
        verify(contentSecurityService, never()).checkText(any(), any(), any(Integer.class));
    }

    @Test
    @DisplayName("updateProfile：头像地址非法 → 拒绝（仅允许站内资源或微信云存储，防外链注入）")
    void updateProfileRejectsIllegalAvatar() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "active"));
        when(imageUrlUtil.isValidAvatar("https://evil.example.com/x.png")).thenReturn(false);
        ProfileUpdateReq req = new ProfileUpdateReq();
        req.setAvatar("https://evil.example.com/x.png");

        assertThatThrownBy(() -> service().updateProfile(1L, req))
                .isInstanceOf(BusinessException.class);
        verify(userMapper, never()).update(any());
    }

    @Test
    @DisplayName("updateProfile：合法昵称 → 走内容安全检测(scene=1) 且用 LambdaUpdateWrapper 部分更新（非整行覆盖）")
    void updateProfilePartiallyUpdatesWithContentSecurity() {
        User before = user(1L, "active");
        before.setOpenid("oX-openid");
        when(userMapper.selectById(1L)).thenReturn(before);
        when(localSensitiveFilter.containsSensitive("新昵称")).thenReturn(false);
        ProfileUpdateReq req = new ProfileUpdateReq();
        req.setNickname("新昵称");

        // 第二次 selectById 返回更新后的行
        when(userMapper.selectById(1L)).thenReturn(before, user(1L, "active"));

        service().updateProfile(1L, req);

        verify(contentSecurityService).checkText("oX-openid", "新昵称", 1);
        // 部分更新走 update(wrapper)；若退化为 updateById 会覆盖 bind_email/status 造成 lost update
        verify(userMapper).update(any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class));
        verify(userMapper, never()).updateById(any());
    }
}

