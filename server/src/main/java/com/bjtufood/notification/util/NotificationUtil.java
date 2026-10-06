package com.bjtufood.notification.util;

import com.bjtufood.notification.dto.NotificationCmd;
import com.bjtufood.notification.service.NotificationService;

/**
 * 通知投递工具：回执失败不阻塞业务主流程。
 * <p>
 * <b>边界说明（D2 澄清）</b>：本 catch 只拦得住「向线程池提交任务」阶段的异常（如池已关闭），
 * <b>拦不住「异步线程内写库失败」</b>——{@code @Async} 下异步线程的异常不回传调用方。
 * 真正的写入失败由 {@code NotificationServiceImpl#notify} 内部 catch 就地记 error 日志，不静默；
 * 排查丢通知只能看日志。
 */
public final class NotificationUtil {

    private NotificationUtil() {
    }

    public static void notifySafe(NotificationService notificationService, NotificationCmd cmd) {
        try {
            notificationService.notify(cmd);
        } catch (Exception ignored) {
            // 回执失败不阻塞业务主流程
        }
    }
}
