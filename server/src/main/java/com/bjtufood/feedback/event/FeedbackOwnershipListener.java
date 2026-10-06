package com.bjtufood.feedback.event;

import com.bjtufood.auth.event.UserOwnershipMigratedEvent;
import com.bjtufood.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号归属迁移 → 反馈改挂（跨域写侧解耦：跨域归属迁移由 auth 域发
 * {@code UserOwnershipMigratedEvent}，各域自行改本域表——auth 不直接改他域数据）。
 * <p>
 * 账号注销不在此处理：注销时 user_feedback 的行与 {@code user_id} 保持不动，
 * 昵称由 join user 实时取「已注销用户」（既有口径，见 {@code UserAccountClosedEvent} 注释）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackOwnershipListener {

    private final FeedbackService feedbackService;

    @EventListener
    public void onUserOwnershipMigrated(UserOwnershipMigratedEvent event) {
        int affected = feedbackService.migrateOwnership(event.fromUserId(), event.toUserId());
        if (affected > 0) {
            log.info("账号归属迁移反馈改挂 fromUserId={} toUserId={} affected={}",
                    event.fromUserId(), event.toUserId(), affected);
        }
    }
}

