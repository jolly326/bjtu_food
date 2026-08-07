package com.bjtufood.notify.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.notify.dto.NotificationVO;
import com.bjtufood.notify.entity.Notification;
import com.bjtufood.notify.mapper.NotificationMapper;
import com.bjtufood.notify.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息通知接口（task-09，STU）
 */
@Tag(name = "09. 消息通知", description = "我的消息列表/未读计数/已读。学生态。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    @Operation(summary = "我的消息列表", description = "STU。倒序，支持 isRead 过滤。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my/notifications")
    public Result<PageResult<NotificationVO>> list(
            @Parameter(description = "已读过滤：0/1（可空=全部）")
            @RequestParam(required = false) Integer isRead,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notification> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreatedAt);
        if (isRead != null) w.eq(Notification::getIsRead, isRead);
        IPage<Notification> p = notificationMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize), w);
        IPage<NotificationVO> result = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize, p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
    }

    @Operation(summary = "未读总数", description = "STU。驱动首页红点。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my/notifications/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        Long userId = SecurityUtil.getCurrentUserId();
        long count = notificationMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        Map<String, Long> data = new HashMap<>();
        data.put("count", count);
        return Result.success(data);
    }

    @Operation(summary = "单条已读", description = "STU 归属校验。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @PutMapping("/my/notifications/{id}/read")
    public Result<Void> readOne(
            @Parameter(description = "通知ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            return Result.success();
        }
        n.setIsRead(1);
        notificationMapper.updateById(n);
        return Result.success();
    }

    @Operation(summary = "全部已读", description = "STU。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @PutMapping("/my/notifications/read-all")
    public Result<Void> readAll() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<Notification> list = notificationMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        for (Notification n : list) {
            n.setIsRead(1);
            notificationMapper.updateById(n);
        }
        return Result.success();
    }

    private NotificationVO toVO(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setType(n.getType());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setRelatedId(n.getRelatedId());
        vo.setIsRead(n.getIsRead());
        vo.setCreatedAt(n.getCreatedAt());
        return vo;
    }
}
