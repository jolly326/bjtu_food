package com.bjtufood.content.broadcast.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.result.Result;
import com.bjtufood.content.broadcast.entity.Broadcast;
import com.bjtufood.content.broadcast.mapper.BroadcastMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台广播管理（首页滚动通知条增删改查，task-14 W6）
 * <p>
 * 与用户端公开接口 {@code GET /broadcasts} 互补：本模块管理全部广播（含 disabled）。
 * broadcastType 取值与用户端一致：NOTICE / ACTIVITY / DISH / URL / NONE。
 */
@Tag(name = "19. 后台广播管理", description = "ADM。首页广播通知条管理。需要管理员 token。")
@RestController
@RequestMapping("/admin/broadcasts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class BroadcastAdminController {

    private final BroadcastMapper broadcastMapper;

    @Operation(summary = "广播列表", description = "ADM。全部广播（含 disabled），按 sort_order 升序、created_at 降序。")
    @GetMapping
    public Result<List<Broadcast>> list() {
        List<Broadcast> list = broadcastMapper.selectList(
                new LambdaQueryWrapper<Broadcast>()
                        .orderByAsc(Broadcast::getSortOrder)
                        .orderByDesc(Broadcast::getCreatedAt));
        return Result.success(list);
    }

    @Operation(summary = "新增广播", description = "ADM。创建首页广播通知条。")
    @PostMapping
    public Result<Long> create(@RequestBody Broadcast b) {
        b.setId(null);
        broadcastMapper.insert(b);
        return Result.success(b.getId());
    }

    @Operation(summary = "编辑广播", description = "ADM。更新广播内容/跳转/排序/状态。")
    @PutMapping("/{id}")
    public Result<Void> update(
            @Parameter(description = "广播ID", example = "1")
            @PathVariable Long id,
            @RequestBody Broadcast b) {
        b.setId(id);
        broadcastMapper.updateById(b);
        return Result.success();
    }

    @Operation(summary = "删除广播", description = "ADM。删除广播。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "广播ID", example = "1")
            @PathVariable Long id) {
        broadcastMapper.deleteById(id);
        return Result.success();
    }
}
