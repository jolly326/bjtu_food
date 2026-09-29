package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.auth.entity.EmailVerificationCode;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DateTimeUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EmailCodeServiceImpl} 单元测试。
 * <p>
 * 这是<b>公开匿名端点</b> {@code POST /auth/email-code} 的唯一防线，故聚焦三处风控语义：
 * <ol>
 *   <li><b>发送频控</b>（60 秒窗口）：缺失即限流是本方法的主要防刷手段，退化为不检查
 *       即允许脚本以任意频率轰炸校园邮箱；</li>
 *   <li><b>先落库后发信</b>：顺序反了会产生「已发送但无记录」的脏数据，
 *       且发信失败若不回删，会留下用户不可用却<b>占用限流窗口</b>的孤儿记录；</li>
 *   <li><b>不存明文验证码</b>：落库须为 BCrypt 哈希，库泄露时不可直接用于认证。</li>
 * </ol>
 * 被测类为纯 POJO：{@code @Value} 用 {@code ReflectionTestUtils} 注入等价字段。
 */
class EmailCodeServiceImplTest {

    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), EmailCodeServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, EmailVerificationCode.class);
    }

    private final EmailVerificationCodeMapper mapper = mock(EmailVerificationCodeMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private EmailCodeServiceImpl service() {
        EmailCodeServiceImpl svc = new EmailCodeServiceImpl(mapper, passwordEncoder, mailSender);
        ReflectionTestUtils.setField(svc, "mailFrom", "noreply@bjtu.edu.cn");
        return svc;
    }

    private static EmailVerificationCode lastRecord(long secondsAgo) {
        EmailVerificationCode r = new EmailVerificationCode();
        r.setEmail("20240001@bjtu.edu.cn");
        r.setPurpose("verify");
        r.setCreatedAt(DateTimeUtil.now().minusSeconds(secondsAgo));
        return r;
    }

    /**
     * 让 mock 模拟 MyBatis 的主键回填（真实 {@code insert} 会把自增 ID 写回实体）。
     * <p>
     * 不模拟的话 {@code record.getId()} 恒为 null，回滚断言就只能写成
     * {@code deleteById(any())}——那样<b>测不出回滚删的是不是刚插入的那条</b>，
     * 退化成「随便删个什么都行」也能通过。回填后可断言具体 ID。
     */
    private void givenInsertBackfillsId(long id) {
        doAnswer(inv -> {
            ((EmailVerificationCode) inv.getArgument(0)).setId(id);
            return 1;
        }).when(mapper).insert(any(EmailVerificationCode.class));
    }

    // ==================== 入参与校园邮箱校验 ====================

    @Test
    @DisplayName("学号为空/空白 → 400「请填写学号」，且不查库不发信")
    void blankUsernameRejected() {
        EmailCodeServiceImpl svc = service();
        for (String bad : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> svc.sendCode(bad))
                    .satisfies(ex -> assertThat(((BusinessException) ex).getMessage()).isEqualTo("请填写学号"));
        }
        verify(mapper, never()).insert(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // ==================== 发送频控（防刷核心）====================

    @Test
    @DisplayName("60 秒内重复请求 → 拒绝且不落库不发信（防刷核心）")
    void secondRequestWithinWindowIsRateLimited() {
        when(mapper.selectOne(any())).thenReturn(lastRecord(10));

        assertThatThrownBy(() -> service().sendCode("20240001"))
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessage()).contains("发送太频繁"));

        verify(mapper, never()).insert(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("限流提示带剩余秒数且至少为 1（不出现「0 秒后重试」的误导文案）")
    void rateLimitMessageCarriesPositiveSeconds() {
        when(mapper.selectOne(any())).thenReturn(lastRecord(58));

        assertThatThrownBy(() -> service().sendCode("20240001"))
                .satisfies(ex -> {
                    String msg = ((BusinessException) ex).getMessage();
                    assertThat(msg).contains("发送太频繁");
                    int sec = Integer.parseInt(msg.replaceAll("\\D+", ""));
                    assertThat(sec).isGreaterThanOrEqualTo(1);
                });
    }

    @Test
    @DisplayName("超过 60 秒窗口 / 无历史记录 / 历史记录无时间戳 → 放行")
    void rateLimitAllowsWhenWindowElapsedOrNoRecord() {
        when(mapper.selectOne(any())).thenReturn(lastRecord(120));
        assertThatCode(() -> service().sendCode("20240001")).doesNotThrowAnyException();

        when(mapper.selectOne(any())).thenReturn(null);
        assertThatCode(() -> service().sendCode("20240002")).doesNotThrowAnyException();

        EmailVerificationCode noTs = new EmailVerificationCode();   // createdAt=null 的历史脏数据
        when(mapper.selectOne(any())).thenReturn(noTs);
        assertThatCode(() -> service().sendCode("20240003")).doesNotThrowAnyException();
    }

    // ==================== 先落库后发信 ====================

    @Test
    @DisplayName("成功路径：先 insert 后发信，且落库为哈希（不存明文验证码）")
    void insertsHashBeforeSendingMail() {
        when(mapper.selectOne(any())).thenReturn(lastRecord(120));
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hashed");

        service().sendCode("20240001");

        ArgumentCaptor<EmailVerificationCode> rec = ArgumentCaptor.forClass(EmailVerificationCode.class);
        verify(mapper).insert(rec.capture());
        EmailVerificationCode saved = rec.getValue();
        // 落库为哈希，6 位明文码只在内存与邮件中出现
        assertThat(saved.getCodeHash()).isEqualTo("$2a$10$hashed");
        assertThat(saved.getEmail()).isEqualTo("20240001@bjtu.edu.cn");
        assertThat(saved.getPurpose()).isEqualTo("verify");
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("20240001@bjtu.edu.cn");
        assertThat(mail.getValue().getFrom()).isEqualTo("noreply@bjtu.edu.cn");
    }

    @Test
    @DisplayName("发信失败 → 回滚已落库记录（避免孤儿码占用限流窗口）并原样抛出")
    void mailFailureRollsBackRecord() {
        when(mapper.selectOne(any())).thenReturn(lastRecord(120));
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hashed");
        givenInsertBackfillsId(4242L);
        doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service().sendCode("20240001"))
                .isInstanceOf(RuntimeException.class);

        // 必须精确回删「刚插入的那条」(4242)，而非任意 ID
        verify(mapper).deleteById(4242L);
    }

    @Test
    @DisplayName("SMTP 发件邮箱未配置 → 明确报错（不静默吞掉），且同样回滚已落库记录")
    void missingMailFromIsReported() {
        when(mapper.selectOne(any())).thenReturn(lastRecord(120));
        givenInsertBackfillsId(4242L);
        EmailCodeServiceImpl svc = new EmailCodeServiceImpl(mapper, passwordEncoder, mailSender);
        ReflectionTestUtils.setField(svc, "mailFrom", "");

        assertThatThrownBy(() -> svc.sendCode("20240001"))
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessage()).contains("SMTP"));
        // 配置缺失发生在「已落库」之后，同样不能留下占用限流窗口的孤儿码
        verify(mapper).deleteById(4242L);
    }
}
