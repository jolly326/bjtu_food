package com.bjtufood.notify.controller;

import com.bjtufood.common.annotation.RequireVerified;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.notify.dto.NotificationVO;
import com.bjtufood.notify.dto.UnreadCountVO;
import com.bjtufood.notify.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    public Result<UnreadCountVO> unreadCount() {
        long count = notificationService.countUnread(SecurityUtil.getCurrentUserId());
        return Result.success(new UnreadCountVO(count));
    }

    /**
     * 全部已读（spec §7.18）。
     * <p>
     * 路径冲突规避：本方法声明在 {@link #readAll()} 之前只是「字面路径优先」的额外保险，
     * 真正的规避点在于单条已读路径是 {@code /my/notifications/{id}/read}（含后缀 {@code /read}），
     * 而本方法是 {@code /my/notifications/read-all}（无后缀），二者**结构不同、不存在映射歧义**：
     * {@code read-all} 不会被当作 {@code {id}} 解析，因为 {@code {id}} 那条要求路径末尾必须多一段
     * {@code /read}，且 {@code id} 绑定为 {@code Long}（把 {@code read-all} 与 {@code {id}} 直接比较时
     * 会因类型转换失败而不匹配，从而落到本方法）。
     */
    @Operation(summary = "全部已读", description = "STU（需邮箱认证）。一次性把当前用户全部未读置为已读，返回本次置读条数；幂等。",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @RequireVerified
    @PutMapping("/my/notifications/read-all")
    public Result<Integer> readAll() {
        return Result.success(notificationService.markAllRead(SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "单条已读", description = "STU（需邮箱认证）。通知不存在或非本人时静默成功（不报错、不暴露他人通知存在性）。", security = @SecurityRequirement(name = "bearerAuth"))
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
