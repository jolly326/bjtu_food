package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.auth.entity.EmailVerificationCode;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
import com.bjtufood.auth.service.EmailCodeService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DateTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
public class EmailCodeServiceImpl implements EmailCodeService {

    private static final long SEND_INTERVAL_SECONDS = 60;
    private static final long CODE_EXPIRE_MINUTES = 10;
    private static final String PURPOSE = "verify";

    private final EmailVerificationCodeMapper emailVerificationCodeMapper;
    private final PasswordEncoder passwordEncoder;
    /**
     * 邮件发送器。<b>不设为必需依赖</b>：Spring Boot 的
     * {@code MailSenderAutoConfiguration} 带 {@code @ConditionalOnProperty("spring.mail.host")}
     * —— 云端漏注入 {@code SPRING_MAIL_USERNAME} / {@code SPRING_MAIL_PASSWORD} 时该 Bean 不存在，
     * 而本类是 {@code authServiceImpl} 的<b>传递依赖</b>（构造参数 5），
     * 于是「一个可选的邮件功能未配置」升级为「<b>整个应用启动失败</b>」，
     * 表现为就绪探针 connection refused，且日志被平台截断后极难定位（2026-10-02 线上事故）。
     *
     * <p>改用 {@code ObjectProvider} 惰性取：Bean 存在则注入，不存在则置空，
     * 由 {@link #sendCode} 显式判空并抛出可读业务异常（400）——
     * 既不拖垮启动，也<b>不静默吞掉</b>（用户点「发送验证码」会明确收到「邮件服务未配置」）。
     *
     * <p>本类<b>不再用 {@code @RequiredArgsConstructor}</b>：该注解会把所有 final 字段
     * 合成<b>必需</b>构造参数，无法表达「可选」。故显式写构造器。
     */
    private final JavaMailSender mailSender;

    private final SecureRandom secureRandom = new SecureRandom();

    public EmailCodeServiceImpl(EmailVerificationCodeMapper emailVerificationCodeMapper,
                                PasswordEncoder passwordEncoder,
                                ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.emailVerificationCodeMapper = emailVerificationCodeMapper;
        this.passwordEncoder = passwordEncoder;
        // getIfAvailable()：未配置 spring.mail.* 时返回 null 而非抛 NoSuchBeanDefinitionException
        this.mailSender = mailSenderProvider.getIfAvailable();
        if (this.mailSender == null) {
            log.warn("未检测到 JavaMailSender（spring.mail.username / password 未注入）——"
                    + "应用可正常启动，但邮箱认证「发送验证码」将不可用");
        }
    }

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Override
    public void sendCode(String username) {
        // 未配置 SMTP 时的显式降级：不静默失败（用户点了没反应最糟），
        // 也不让「一个可选功能未配置」升级为 500 未捕获异常。
        // ⚠️ 文案保留「SMTP」字样：既有测试 missingMailFromIsReported 断言该词，
        // 且对用户而言「SMTP 未配置」比「邮件服务未配置」更明确指向原因。
        if (mailSender == null || !StringUtils.hasText(mailFrom)) {
            log.error("SMTP 未配置（spring.mail.username / password 未注入），无法发送验证码");
            throw new BusinessException(500, "SMTP 邮件服务未配置，暂时无法发送验证码，请联系管理员");
        }
        String normalizedEmail = resolveEmail(username);
        validateCampusEmail(normalizedEmail);

        checkRateLimit(normalizedEmail, PURPOSE);

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        // 先落库、后发邮件：即便发邮件失败，也不产生「已发送但无记录」的脏数据。
        EmailVerificationCode record = new EmailVerificationCode();
        record.setEmail(normalizedEmail);
        record.setCodeHash(passwordEncoder.encode(code));
        record.setPurpose(PURPOSE);
        record.setExpiresAt(DateTimeUtil.now().plusMinutes(CODE_EXPIRE_MINUTES));
        emailVerificationCodeMapper.insert(record);

        try {
            sendEmail(normalizedEmail, code);
        } catch (Exception e) {
            // 发邮件失败 → 回滚已落库记录，避免留下孤儿验证码（用户不可用但占用限流窗口）
            emailVerificationCodeMapper.deleteById(record.getId());
            throw e;
        }

        log.info("Email verification code sent to {}", normalizedEmail);
    }

    /**
     * 校园邮箱规则：邮箱 = {学号}@bjtu.edu.cn，由学号推导。
     */
    private String resolveEmail(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException("请填写学号");
        }
        return username.trim().toLowerCase(Locale.ROOT) + "@bjtu.edu.cn";
    }

    private void sendEmail(String to, String code) {
        if (!StringUtils.hasText(mailFrom)) {
            throw new BusinessException("SMTP 发件邮箱未配置，请设置 MAIL_USERNAME");
        }

        // 认证用途 verify（替代旧 login/register/reset）
        String subject = "学号邮箱认证验证码";
        String text = String.format("""
                您正在进行学号邮箱认证。

                您的验证码为：%s

                验证码 %d 分钟内有效，请勿泄露给他人。

                -- 食在交大 校园食堂信息系统
                """, code, CODE_EXPIRE_MINUTES);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject("【食在交大】" + subject);
        message.setText(text.trim());

        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}", to, e);
            throw new BusinessException("验证码发送失败，请检查 SMTP 配置后重试");
        }
    }

    private void checkRateLimit(String email, String purpose) {
        EmailVerificationCode lastRecord = emailVerificationCodeMapper.selectOne(
                new LambdaQueryWrapper<EmailVerificationCode>()
                        .eq(EmailVerificationCode::getEmail, email)
                        .eq(EmailVerificationCode::getPurpose, purpose)
                        .orderByDesc(EmailVerificationCode::getCreatedAt)
                        .last("LIMIT 1"));

        if (lastRecord == null || lastRecord.getCreatedAt() == null) {
            return;
        }

        LocalDateTime nextAllowedAt = lastRecord.getCreatedAt().plusSeconds(SEND_INTERVAL_SECONDS);
        if (nextAllowedAt.isAfter(DateTimeUtil.now())) {
            long remainingSeconds = Duration.between(DateTimeUtil.now(), nextAllowedAt).getSeconds();
            throw new BusinessException("发送太频繁，请 " + Math.max(1, remainingSeconds) + " 秒后重试");
        }
    }

    private void validateCampusEmail(String email) {
        if (!email.endsWith("@bjtu.edu.cn")) {
            throw new BusinessException("请使用北京交通大学校园邮箱");
        }
    }
}
