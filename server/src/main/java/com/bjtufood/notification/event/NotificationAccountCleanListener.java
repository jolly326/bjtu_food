package com.bjtufood.notification.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bjtufood.auth.event.UserAccountClosedEvent;
import com.bjtufood.auth.event.UserOwnershipMigratedEvent;
import com.bjtufood.notification.entity.Notification;
import com.bjtufood.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号注销 / 合并 → 站内消息归属维护（P0-1 写侧解耦）。
 * <p>
 * 注销：<b>硬删</b>该用户全部消息；
 * 合并：消息改挂正式账号。
 * <p>
 * <b>事务语义</b>：不经 {@code NotificationService} 的 {@code @Async} 入口，
 * 由监听器在注销/合并事务内<b>同步</b>执行——消息清理与账号操作要么同时成功、要么同时回滚，
 * 不出现「账号已合并但消息仍挂在已注销账号上」的中间态。
 * <p>
 * 本类位于 notify 域内，直接操作本域 Mapper（不污染 {@code NotificationService} 的对外契约）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationAccountCleanListener {

    private final NotificationMapper notificationMapper;

    @EventListener
    public void onUserAccountClosed(UserAccountClosedEvent event) {
        int affected = notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, event.userId()));
        if (affected > 0) {
            log.info("账号注销删除站内通知 userId={} affected={}", event.userId(), affected);
        }
    }

    @EventListener
    public void onUserOwnershipMigrated(UserOwnershipMigratedEvent event) {
        int affected = notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, event.fromUserId())
                .set(Notification::getUserId, event.toUserId()));
        if (affected > 0) {
            log.info("账号合并站内通知改挂 fromUserId={} toUserId={} affected={}",
                    event.fromUserId(), event.toUserId(), affected);
        }
    }
}
