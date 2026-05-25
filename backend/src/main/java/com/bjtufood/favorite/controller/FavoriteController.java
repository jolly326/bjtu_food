package com.bjtufood.favorite.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 收藏控制器
 * <p>
 * 学生用户收藏/取消收藏菜品，查看收藏列表。
 * 操作后发布事件，由 dish 模块同步更新收藏量。
 */
@Tag(name = "收藏管理", description = "收藏/取消收藏、我的收藏列表、批量收藏")
@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "切换收藏状态", description = "已收藏则取消，未收藏则新增（幂等操作）")
    @PostMapping("/toggle")
    public Result<?> toggle(@RequestBody Map<String, Long> body) {
        // TODO: 获取当前 userId，调用 FavoriteService.toggle(userId, body.get("dishId"))
        // 返回 { "favorited": true/false }
        return Result.success("收藏状态");
    }

    @Operation(summary = "我的收藏列表", description = "查看当前用户已收藏的菜品列表（分页）")
    @GetMapping
    public Result<?> listMyFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        // TODO: 获取当前 userId，调用 FavoriteService.listByUserId(userId, page, pageSize)
        return Result.success("收藏列表");
    }

    @Operation(summary = "批量收藏", description = "供清单一键收藏功能调用，已收藏的自动跳过")
    @PostMapping("/batch")
    public Result<?> batchCollect(@RequestBody Map<String, Object> body) {
        // TODO: 获取当前 userId，从 body 获取 dishIds 列表
        // 调用 FavoriteService.batchCollect(userId, dishIds)
        // 返回 { "succeeded": 3, "skipped": 0 }
        return Result.success("批量收藏完成");
    }
}
