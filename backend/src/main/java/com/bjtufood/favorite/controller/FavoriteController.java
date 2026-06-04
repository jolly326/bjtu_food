package com.bjtufood.favorite.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @Operation(summary = "切换收藏状态", description = "已收藏则取消，未收藏则新增（幂等操作，自动同步 dish.favorite_count）")
    @PostMapping("/toggle")
    public Result<?> toggle(@RequestBody Map<String, Long> body) {
        Long userId = SecurityUtil.getCurrentUserId();
        Long dishId = body.get("dishId");
        if (dishId == null) {
            return Result.badRequest("dishId 不能为空");
        }
        boolean favorited = favoriteService.toggle(userId, dishId);
        return Result.success(Map.of("favorited", favorited));
    }

    @Operation(summary = "我的收藏列表", description = "查看当前用户已收藏的菜品列表（分页）")
    @GetMapping
    public Result<?> listMyFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(favoriteService.listFavoriteDishes(userId, page, pageSize));
    }

    @Operation(summary = "批量收藏", description = "供清单一键收藏功能调用，已收藏的自动跳过")
    @PostMapping("/batch")
    public Result<?> batchCollect(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("dishIds");
        if (rawIds == null || rawIds.isEmpty()) {
            return Result.badRequest("dishIds 不能为空");
        }
        Long userId = SecurityUtil.getCurrentUserId();
        List<Long> dishIds = rawIds.stream().map(Long::valueOf).toList();
        Map<String, Integer> result = favoriteService.batchCollect(userId, dishIds);
        return Result.success(result);
    }
}
