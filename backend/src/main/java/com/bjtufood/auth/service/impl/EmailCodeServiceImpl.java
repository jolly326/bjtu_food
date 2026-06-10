package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.auth.entity.EmailVerificationCode;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
import com.bjtufood.auth.service.EmailCodeService;
import com.bjtufood.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class EmailCodeServiceImpl implements EmailCodeService {

    private static final long SEND_INTERVAL_SECONDS = 60;
    private static final long CODE_EXPIRE_MINUTES = 10;

    private final EmailVerificationCodeMapper emailVerificationCodeMapper;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Override
    public void sendCode(String email, String purpose) {
        String normalizedEmail = normalizeEmail(email);
        validateCampusEmail(normalizedEmail);
        String normalizedPurpose = normalizePurpose(purpose);

        checkRateLimit(normalizedEmail, normalizedPurpose);

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        sendEmail(normalizedEmail, code, normalizedPurpose);

        EmailVerificationCode record = new EmailVerificationCode();
        record.setEmail(normalizedEmail);
        record.setCodeHash(passwordEncoder.encode(code));
        record.setPurpose(normalizedPurpose);
        record.setExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRE_MINUTES));
        emailVerificationCodeMapper.insert(record);

        log.info("Email verification code sent to {}, purpose={}", normalizedEmail, normalizedPurpose);
    }

    private void sendEmail(String to, String code, String purpose) {
        if (!StringUtils.hasText(mailFrom)) {
            throw new BusinessException("SMTP 发件邮箱未配置，请设置 MAIL_USERNAME");
        }

        String subject = switch (purpose) {
            case "register" -> "注册验证码";
            case "reset" -> "重置密码验证码";
            default -> "登录验证码";
        };
        String text = String.format("""
                您的%s为：%s

                验证码 %d 分钟内有效，请勿泄露给他人。

                -- 食在交大 校园食堂信息系统
                """, subject, code, CODE_EXPIRE_MINUTES);

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
        if (nextAllowedAt.isAfter(LocalDateTime.now())) {
            long remainingSeconds = Duration.between(LocalDateTime.now(), nextAllowedAt).getSeconds();
            throw new BusinessException("发送太频繁，请 " + Math.max(1, remainingSeconds) + " 秒后重试");
        }
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException("邮箱不能为空");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePurpose(String purpose) {
        if ("register".equalsIgnoreCase(purpose)) {
            return "register";
        }
        if ("reset".equalsIgnoreCase(purpose)) {
            return "reset";
        }
        return "login";
    }

    private void validateCampusEmail(String email) {
        if (!email.endsWith("@bjtu.edu.cn")) {
            throw new BusinessException("请使用北京交通大学校园邮箱");
        }
    }
}
