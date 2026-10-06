package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.auth.constant.UserConst;
import com.bjtufood.auth.entity.EmailVerificationCode;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.event.UserOwnershipMigratedEvent;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DateTimeUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VerifyCodePersister} 单元测试（D1）。
 * <p>
 * 本类是 {@code verifyEmail} 事务边界收窄的落点：「逐条 BCrypt 定位验证码」（最坏约 2 秒）
 * 与「跨域归属迁移」分属两个事务方法。拆分后必须锁住的行为：
 * <ul>
 *   <li><b>验证码原子消费</b>：{@code UPDATE ... WHERE used_at IS NULL} 影响行数必须为 1 才算消费成功，
 *       否则视为被并发抢先、继续试下一条。这是防止同一验证码被用两次的唯一防线；</li>
 *   <li><b>归属迁移的条件判断</b>：释放他微信绑定 → 迁移数据 → 标记历史邮箱账号注销 → 置 bind_email，
 *       命中的行就是本人时不得自我迁移（否则会发出 from==to 的无意义事件）。</li>
 * </ul>
 * 被测类为纯 POJO：{@code @Transactional} 依赖 Spring 代理，单测中不生效，
 * 断言的是<b>方法体内的业务逻辑</b>。
 */
class VerifyCodePersisterTest {

    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), VerifyCodePersisterTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, EmailVerificationCode.class);
    }

    private final EmailVerificationCodeMapper codeMapper = mock(EmailVerificationCodeMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private VerifyCodePersister persister() {
        return new VerifyCodePersister(codeMapper, userMapper, passwordEncoder, eventPublisher);
    }

    private static EmailVerificationCode code(long id, String email) {
        EmailVerificationCode record = new EmailVerificationCode();
        record.setId(id);
        record.setEmail(email);
        record.setCodeHash("hash-" + id);
        record.setPurpose("verify");
        record.setCreatedAt(DateTimeUtil.now());
        record.setExpiresAt(DateTimeUtil.now().plusMinutes(10));
        return record;
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setStatus(UserConst.STATUS_ACTIVE);
        u.setNickname("小明");
        return u;
    }

    /** 断言抛出的是带指定文案的业务异常（避免每个用例重复强制转换样板） */
    private static void assertBusinessMessage(String expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .hasMessage(expected);
    }

    // ==================== consumeAndGetEmail：原子消费 ====================

    @Test
    @DisplayName("消费：匹配命中且 CAS 影响行数=1 → 返回邮箱并置 used_at")
    void consumeReturnsEmailOnCasSuccess() {
        when(codeMapper.selectList(any())).thenReturn(List.of(code(1L, "20240001@bjtu.edu.cn")));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        // 必须打单参 update(Wrapper) 重载：生产代码走的正是这一个
        when(codeMapper.update(any())).thenReturn(1);

        assertThat(persister().consumeAndGetEmail("123456")).isEqualTo("20240001@bjtu.edu.cn");

        verify(codeMapper, times(1)).update(any());
    }

    @Test
    @DisplayName("消费：CAS 影响行数=0（已被并发抢先）→ 不得视为消费成功，须报「验证码错误」")
    void consumeTreatsZeroAffectedRowsAsNotConsumed() {
        when(codeMapper.selectList(any())).thenReturn(List.of(code(1L, "a@bjtu.edu.cn")));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        // 关键：CAS 失败（被并发消费）
        when(codeMapper.update(any())).thenReturn(0);

        // 不得把「已用」当成「匹配成功」返回——那会让同一验证码被用第二次
        assertBusinessMessage("验证码错误", () -> persister().consumeAndGetEmail("123456"));
    }

    @Test
    @DisplayName("消费：候选为空 → 「验证码不存在或已过期」，零副作用")
    void consumeRejectsEmptyCandidates() {
        when(codeMapper.selectList(any())).thenReturn(List.of());

        assertBusinessMessage("验证码不存在或已过期", () -> persister().consumeAndGetEmail("123456"));

        verify(codeMapper, never()).update(any());
    }

    @Test
    @DisplayName("消费：空码 → 直接拒绝，不查库（省一次无谓查询）")
    void consumeRejectsBlankCodeWithoutQuery() {
        assertBusinessMessage("验证码不能为空", () -> persister().consumeAndGetEmail("  "));

        verify(codeMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("消费：BCrypt 抛 IllegalArgumentException（脏散列）按不匹配处理，不得中断整批比对")
    void consumeTreatsCorruptHashAsMismatch() {
        when(codeMapper.selectList(any())).thenReturn(List.of(code(1L, "a@bjtu.edu.cn")));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Encoded password does not look like BCrypt"));

        assertBusinessMessage("验证码错误", () -> persister().consumeAndGetEmail("123456"));
    }

    // ==================== applyVerifiedBinding：归属迁移 ====================

    @Test
    @DisplayName("写入：无历史账号冲突 → 仅置 bind_email，不发迁移事件")
    void appliesBindingWithoutMigration() {
        when(userMapper.selectOne(any())).thenReturn(null);
        User current = user(1L);

        persister().applyVerifiedBinding(current, "20240001@bjtu.edu.cn");

        assertThat(current.getBindEmail()).isEqualTo("20240001@bjtu.edu.cn");
        verify(userMapper).updateById(current);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("写入：该邮箱已被他微信认证 → 释放旧绑定 + 发布归属迁移事件（数据改挂当前账号）")
    void releasesOldBindingAndMigrates() {
        // 第 1 次 selectOne=旧微信绑定行；第 2 次=历史邮箱账号（null）
        when(userMapper.selectOne(any())).thenReturn(user(9L)).thenReturn(null);
        User current = user(1L);

        persister().applyVerifiedBinding(current, "20240001@bjtu.edu.cn");

        // 旧微信必须被解绑（bind_email 置 NULL），否则唯一键占用不释放。
        // 注意生产代码走的是**两参** update(entity, wrapper) 重载（实体位传 null + LambdaUpdateWrapper），
        // 打桩必须匹配该签名，否则匹配不上会静默漏验证。
        verify(userMapper, times(1)).update(isNull(), any());
        // 关键断言：数据必须迁移到当前账号，否则旧账号掉登录态但数据悬空
        ArgumentCaptor<UserOwnershipMigratedEvent> evt = ArgumentCaptor.forClass(UserOwnershipMigratedEvent.class);
        verify(eventPublisher).publishEvent(evt.capture());
        assertThat(evt.getValue().fromUserId()).isEqualTo(9L);
        assertThat(evt.getValue().toUserId()).isEqualTo(1L);
        assertThat(current.getBindEmail()).isEqualTo("20240001@bjtu.edu.cn");
    }

    @Test
    @DisplayName("写入：命中的绑定行就是当前账号本人 → 不得自我迁移（否则会发 from==to 的无意义事件）")
    void skipsSelfMigration() {
        // 两次 selectOne 都返回「当前账号自己」（bind_email 与 email 都指向本人）
        when(userMapper.selectOne(any())).thenReturn(user(1L)).thenReturn(user(1L));
        User current = user(1L);

        persister().applyVerifiedBinding(current, "20240001@bjtu.edu.cn");

        // 关键断言：from==to 应被过滤掉，不发事件、不做任何 UPDATE
        verify(eventPublisher, never()).publishEvent(any());
        verify(userMapper, never()).update(isNull(), any());
        verify(userMapper).updateById(current);
        assertThat(current.getBindEmail()).isEqualTo("20240001@bjtu.edu.cn");
    }

    @Test
    @DisplayName("写入：历史邮箱账号冲突 → 迁移其数据并标记注销、释放 email 唯一键")
    void migratesLegacyEmailAccount() {
        // 第 1 次=微信绑定行（null）；第 2 次=历史邮箱账号
        when(userMapper.selectOne(any())).thenReturn(null).thenReturn(user(9L));
        User current = user(1L);

        persister().applyVerifiedBinding(current, "20240001@bjtu.edu.cn");

        ArgumentCaptor<UserOwnershipMigratedEvent> evt = ArgumentCaptor.forClass(UserOwnershipMigratedEvent.class);
        verify(eventPublisher).publishEvent(evt.capture());
        assertThat(evt.getValue().fromUserId()).isEqualTo(9L);
        // 历史账号必须被标记注销（否则它仍可登录，且 email 唯一键占用不放）。
        // 同样注意两参 update(entity=null, wrapper) 重载。
        verify(userMapper, times(1)).update(isNull(), any());
        assertThat(current.getBindEmail()).isEqualTo("20240001@bjtu.edu.cn");
    }
}