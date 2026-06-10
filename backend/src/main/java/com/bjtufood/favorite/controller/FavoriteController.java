package com.bjtufood.favorite.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.favorite.dto.FavoriteBatchReq;
import com.bjtufood.favorite.dto.FavoriteToggleReq;
import com.bjtufood.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "04. 收藏", description = "我的收藏、切换收藏状态、批量收藏。全部接口需要登录。")
@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(
            summary = "收藏/取消收藏",
            description = "用途：菜品详情页或列表页点击收藏按钮。已收藏则取消，未收藏则新增，并同步 dish.favorite_count。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "dishId": 1
                    }
                    """)))
    )
    @PostMapping("/toggle")
    public Result<?> toggle(@Valid @RequestBody FavoriteToggleReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        boolean favorited = favoriteService.toggle(userId, req.getDishId());
        return Result.success(Map.of("favorited", favorited));
    }

    @Operation(summary = "我的收藏列表", description = "用途：个人中心/收藏页展示当前用户收藏的菜品。测试示例：/favorites?page=1&pageSize=50")
    @GetMapping
    public Result<?> listMyFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(favoriteService.listFavoriteDishes(userId, page, pageSize));
    }

    @Operation(
            summary = "批量收藏",
            description = "用途：美食清单一键收藏。已经收藏的菜品会跳过，返回成功数和跳过数。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "dishIds": [1, 2, 3]
                    }
                    """)))
    )
    @PostMapping("/batch")
    public Result<?> batchCollect(@Valid @RequestBody FavoriteBatchReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(favoriteService.batchCollect(userId, req.getDishIds()));
    }
}
