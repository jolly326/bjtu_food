package com.bjtufood.auth.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.auth.entity.EmailVerificationCode;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 邮箱验证码清理定时任务
 * <p>
 * 每天凌晨 3:00 清理一天前已过期的验证码记录，避免表数据长期堆积。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationCodeCleanupTask {

    private final EmailVerificationCodeMapper emailVerificationCodeMapper;

    /**
     * 每天 03:00 执行一次
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredCodes() {
        // 异常必须内部消化：调度线程抛出异常会导致该任务后续不再被触发
        try {
            LocalDateTime deadline = LocalDateTime.now().minusDays(1);
            // Wrapper 生成参数化 SQL（? 占位），无拼接注入风险
            int deleted = emailVerificationCodeMapper.delete(
                    new LambdaQueryWrapper<EmailVerificationCode>()
                            .lt(EmailVerificationCode::getExpiresAt, deadline));
            if (deleted > 0) {
                log.info("已清理 {} 条一天前过期的验证码记录", deleted);
            }
        } catch (Exception e) {
            log.error("清理过期验证码失败，本次跳过", e);
        }
    }
}
