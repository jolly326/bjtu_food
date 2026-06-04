package com.bjtufood.dish.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.dish.service.DishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "03. 菜品浏览", description = "公开菜品查询、热门菜品、菜品详情、浏览量记录。")
@RestController
@RequestMapping
@RequiredArgsConstructor
@Validated
public class DishController {

    private final DishService dishService;

    @Operation(summary = "热门菜品 TOP10", description = "用途：首页热门推荐。按收藏数、评分等规则返回热门菜品。测试：直接调用即可。")
    @GetMapping("/dishes/hot")
    public Result<List<DishVO>> getHotDishes() {
        return Result.success(dishService.getHotDishes());
    }

    @Operation(
            summary = "菜品分页查询",
            description = """
                    用途：菜品列表页、搜索页、筛选页。
                    测试示例：/dishes?page=1&pageSize=10&keyword=牛肉
                    常用参数：keyword、canteenId、stallId、tag、minPrice、maxPrice、sortBy、sortOrder。
                    """
    )
    @GetMapping("/dishes")
    public Result<?> listDishes(@ModelAttribute DishQueryReq req) {
        return Result.success(dishService.listDishes(req));
    }

    @Operation(
            summary = "菜品详情",
            description = """
                    用途：菜品详情页。
                    未登录可访问；如果已登录并携带 token，会额外返回 isFavorited、hasReviewed。
                    测试示例：/dishes/1
                    """
    )
    @GetMapping("/dishes/{id}")
    public Result<?> getDishDetail(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        return Result.success(dishService.getDishDetail(id, userId));
    }

    @Operation(
            summary = "增加浏览量",
            description = "用途：进入菜品详情页时调用一次。需要登录，用于记录真实用户浏览行为。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/dishes/{id}/view")
    public Result<Void> addView(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        dishService.addViewCount(id, userId);
        return Result.success();
    }
}
