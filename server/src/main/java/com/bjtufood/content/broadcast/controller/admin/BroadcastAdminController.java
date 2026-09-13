package com.bjtufood.content.broadcast.controller.admin;

import com.bjtufood.common.result.Result;
import com.bjtufood.content.broadcast.dto.BroadcastReq;
import com.bjtufood.content.broadcast.entity.Broadcast;
import com.bjtufood.content.broadcast.service.BroadcastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台广播管理（首页滚动通知条增删改查，task-14 W6）
 * <p>
 * 与用户端公开接口 {@code GET /broadcasts} 互补：本模块管理全部广播（含 disabled）。
 * broadcastType 取值与用户端一致：NOTICE / ACTIVITY / DISH / URL / NONE。
 * <p>
 * P3/ARCH-008：CRUD 下沉 BroadcastService；请求体由 BroadcastReq 承接
 * （@Valid 校验），实体不再直接收请求体。新增走 OnCreate 分组全量必填，
 * 编辑保持部分字段更新（Web 行内启停仅传 status）。
 */
@Tag(name = "19. 后台广播管理", description = "ADM。首页广播通知条管理。需要管理员 token。")
@RestController
@RequestMapping("/admin/broadcasts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class BroadcastAdminController {

    private final BroadcastService broadcastService;

    @Operation(summary = "广播列表", description = "ADM。全部广播（含 disabled），按 sort_order 升序、created_at 降序。")
    @GetMapping
    public Result<List<Broadcast>> list() {
        return Result.success(broadcastService.listAll());
    }

    @Operation(summary = "新增广播", description = "ADM。创建首页广播通知条。title/content/broadcastType 必填。")
    @PostMapping
    public Result<Long> create(
            @Validated({Default.class, BroadcastReq.OnCreate.class}) @RequestBody BroadcastReq req) {
        return Result.success(broadcastService.create(req));
    }

    @Operation(summary = "编辑广播", description = "ADM。更新广播内容/跳转/排序/状态（部分字段更新，未传字段不覆盖）。")
    @PutMapping("/{id}")
    public Result<Void> update(
            @Parameter(description = "广播ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody BroadcastReq req) {
        broadcastService.update(id, req);
        return Result.success();
    }

    @Operation(summary = "删除广播", description = "ADM。删除广播。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "广播ID", example = "1")
            @PathVariable Long id) {
        broadcastService.delete(id);
        return Result.success();
    }
}
