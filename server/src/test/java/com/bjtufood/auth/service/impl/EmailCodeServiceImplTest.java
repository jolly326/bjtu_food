package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.auth.entity.EmailVerificationCode;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DateTimeUtil;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
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

    /**
     * 被测类用 {@code MimeMessage}（为了带发件人显示名）后，须让 {@code createMimeMessage()} 返回<b>真实实例</b>：
     * {@code MimeMessageHelper} 会真实地读写它的 header 与 content，mock 出来的空壳读不到断言所需的正文。
     * <p>
     * {@code Session} 传 {@code null} 是合法的——设置发件人/收件人/主题/正文都不需要会话，
     * 只有真正投递时才用得到，而投递在测试里被 mock 掉了。
     */
    @BeforeEach
    void stubMimeMessage() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    private final EmailVerificationCodeMapper mapper = mock(EmailVerificationCodeMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    /**
     * 把裸 {@link JavaMailSender} mock 包成 {@link ObjectProvider}。
     * <p>被测类改用 {@code ObjectProvider<JavaMailSender>} 惰性注入（2026-10-02 线上事故修复：
     * 云端漏配 {@code spring.mail.*} 导致 {@code JavaMailSender} Bean 缺失，
     * 经 {@code authServiceImpl} 传递依赖放大为**整个应用启动失败**）。
     */
    private static ObjectProvider<JavaMailSender> providerOf(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    private EmailCodeServiceImpl service() {
        EmailCodeServiceImpl svc = new EmailCodeServiceImpl(mapper, passwordEncoder, providerOf(mailSender));
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
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ==================== 发送频控（防刷核心）====================

    @Test
    @DisplayName("60 秒内重复请求 → 拒绝且不落库不发信（防刷核心）")
    void secondRequestWithinWindowIsRateLimited() {
        when(mapper.selectOne(any())).thenReturn(lastRecord(10));

        assertThatThrownBy(() -> service().sendCode("20240001"))
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessage()).contains("发送太频繁"));

        verify(mapper, never()).insert(any());
        verify(mailSender, never()).send(any(MimeMessage.class));
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
    void insertsHashBeforeSendingMail() throws Exception {
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

        ArgumentCaptor<MimeMessage> mail = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(mail.capture());
        MimeMessage sent = mail.getValue();
        // MimeMessage 没有 getTo() 便捷方法（那是 SimpleMailMessage 的），须按 RecipientType 取
        assertThat(((InternetAddress) sent.getRecipients(Message.RecipientType.TO)[0]).getAddress())
                .isEqualTo("20240001@bjtu.edu.cn");
        // 发件人 = 「品牌显示名 + 真实地址」：收件人看到的是「知行食记」，而非一串陌生 SMTP 账号。
        // ⚠️ getPersonal() 拿到的是 encoded-word（如 =?UTF-8?B?...?=），必须解码后再比，否则断言毫无意义。
        InternetAddress from = (InternetAddress) sent.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("noreply@bjtu.edu.cn");
        assertThat(MimeUtility.decodeText(from.getPersonal())).isEqualTo("知行食记");
    }

    @Test
    @DisplayName("发信失败 → 回滚已落库记录（避免孤儿码占用限流窗口）并原样抛出")
    void mailFailureRollsBackRecord() {
        when(mapper.selectOne(any())).thenReturn(lastRecord(120));
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hashed");
        givenInsertBackfillsId(4242L);
        doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service().sendCode("20240001"))
                .isInstanceOf(RuntimeException.class);

        // 必须精确回删「刚插入的那条」(4242)，而非任意 ID
        verify(mapper).deleteById(4242L);
    }

    @Test
    @DisplayName("JavaMailSender Bean 完全缺失（云端漏配 spring.mail.*）→ 明确报错，且**不落库**")
    void missingMailSenderBeanIsReported() {
        // 回归 2026-10-02 线上事故：JavaMailSender 缺失时应用启动失败（经 authServiceImpl 传递依赖放大）。
        // 修复后应「应用能起、发码时报业务错」，且**在落库前**就拦下——不产生孤儿验证码。
        when(mapper.selectOne(any())).thenReturn(lastRecord(120));
        EmailCodeServiceImpl svc = new EmailCodeServiceImpl(mapper, passwordEncoder, providerOf(null));
        ReflectionTestUtils.setField(svc, "mailFrom", "noreply@bjtu.edu.cn");

        assertThatThrownBy(() -> svc.sendCode("20240001"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessage()).contains("SMTP"));

        // 关键：判空发生在 insert 之前，不得留下占用限流窗口的孤儿码
        verify(mapper, never()).insert(any());
    }

    // ==================== 邮件文案（收件人唯一可见的信息面） ====================

    @Test
    @DisplayName("邮件文案：学生端品牌「知行食记」+ 6 位验证码 + 有效期 + 防泄露话术（占位符不得退化）")
    void mailContentCarriesSourceCodeExpiryAndWarning() throws Exception {
        // 为什么对成品邮件正文断言而非断言源码字符串：
        // %s / %d 是 String.format 的硬约束——漏掉占位符会让「验证码」静默缺失（不抛异常），
        // 多写一个裸 % 则抛异常并被 sendCode 回滚记录（用户侧表现为发送失败）。
        // 两种退化都只在成品正文上才现形，故此处捕获真实发出的 MimeMessage。
        when(mapper.selectOne(any())).thenReturn(lastRecord(120));
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hashed");

        service().sendCode("20240001");

        ArgumentCaptor<MimeMessage> mail = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(mail.capture());
        MimeMessage sent = mail.getValue();

        // 主题前缀【知行食记】由 sendEmail 拼接（与发件人显示名同源），是收件人第一眼看到的来源标识
        assertThat(sent.getSubject()).startsWith("【知行食记】");

        // 正文从真实 MimeMessage 取出（MimeMessageHelper 已把文本设为 text/plain 内容）
        String body = (String) sent.getContent();
        // 品牌口径：学生端「知行食记」+ 端上已裁决副标题（逐字对齐 client/src/pages/mine/index.vue）
        assertThat(body).contains("知行食记").contains("北京交通大学 · 校园美食分享圈");
        // ⚠️ 反向护栏：收件人是学生，后端技术名一旦泄漏进正文，会被当成陌生来源的钓鱼邮件，故显式禁止。
        assertThat(body).doesNotContain("校园食堂信息系统");
        // 文本块靠「内容行与结束分隔符缩进对齐」剥离前导空白（incidental whitespace）：
        // 二者错位时每行都会带前导空格并原样进入邮件正文，首行精确断言即该退化的护栏。
        String firstLine = body.lines().findFirst().orElse("");
        assertThat(firstLine).isEqualTo("知行食记");
        // %s 已被替换为 6 位数字码：空文案 / 占位符丢失都过不了这条
        assertThat(body).containsPattern("[0-9]{6}");
        // %d 已被替换为有效期（改 CODE_EXPIRE_MINUTES 时本条不需同步改）
        assertThat(body).containsPattern("[0-9]+ 分钟内有效");
        // 防泄露 + 防钓鱼话术：社工场景下用户在正文里唯一的提示
        assertThat(body).contains("请勿泄露给任何人");
        assertThat(body).contains("不会以任何形式向您索要此验证码");
        // 误填学号 / 他人误触发的兜底出口
        assertThat(body).contains("若这不是您本人的操作");
    }


    @Test
    @DisplayName("SMTP 发件邮箱未配置 → 明确报错（不静默吞掉），且**不落库**（无需回滚）")
    void missingMailFromIsReported() {
        when(mapper.selectOne(any())).thenReturn(lastRecord(120));
        EmailCodeServiceImpl svc = new EmailCodeServiceImpl(mapper, passwordEncoder, providerOf(mailSender));
        ReflectionTestUtils.setField(svc, "mailFrom", "");

        assertThatThrownBy(() -> svc.sendCode("20240001"))
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessage()).contains("SMTP"));
        // 2026-10-02 修复：配置判空已提前到**落库之前**（原实现先 insert 再发信、失败才回滚）。
        // 提前拦截严格更优——压根不产生孤儿码，也就不需要「回滚」这个补救动作。
        // 故此处断言 insert 未发生，而非 deleteById(4242L)。
        verify(mapper, never()).insert(any());
        // deleteById 有重载（Long / Object），须显式给类型消歧，否则 any() 编译不过
        verify(mapper, never()).deleteById(any(Long.class));
    }
}
