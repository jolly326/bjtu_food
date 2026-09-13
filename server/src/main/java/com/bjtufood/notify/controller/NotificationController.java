package com.bjtufood.notify.controller;

import com.bjtufood.common.annotation.RequireVerified;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.notify.dto.NotificationVO;
import com.bjtufood.notify.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 消息通知接口（task-09，STU）
 * <p>
 * P3/ARCH-008：查询/计数/已读逻辑下沉 NotificationService，
 * Controller 只留参数与响应包装，不再注入 Mapper。
 */
@Tag(name = "09. 消息通知", description = "我的消息列表/未读计数/已读。学生态。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "我的消息列表", description = "STU（需邮箱认证）。倒序，支持 isRead 过滤。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @RequireVerified
    @GetMapping("/my/notifications")
    public Result<PageResult<NotificationVO>> list(
            @Parameter(description = "已读过滤：0/1（可空=全部）")
            @RequestParam(required = false) Integer isRead,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(
                notificationService.listMy(SecurityUtil.getCurrentUserId(), isRead, page, pageSize));
    }

    @Operation(summary = "未读总数", description = "STU（需邮箱认证）。驱动首页红点。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @RequireVerified
    @GetMapping("/my/notifications/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        long count = notificationService.countUnread(SecurityUtil.getCurrentUserId());
        return Result.success(Map.of("count", count));
    }

    @Operation(summary = "单条已读", description = "STU（需邮箱认证）归属校验。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @RequireVerified
    @PutMapping("/my/notifications/{id}/read")
    public Result<Void> readOne(
            @Parameter(description = "通知ID", example = "1")
            @PathVariable Long id) {
        notificationService.markRead(SecurityUtil.getCurrentUserId(), id);
        return Result.success();
    }
}
