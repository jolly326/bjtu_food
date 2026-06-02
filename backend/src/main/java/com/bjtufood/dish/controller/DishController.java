package com.bjtufood.dish.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.dish.service.DishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜品公开控制器
 * <p>
 * 无需登录即可浏览菜品列表、查看详情。
 * 浏览量增加需要登录（防刷）。
 */
@Tag(name = "菜品浏览", description = "公开接口，浏览菜品列表、查看详情、热门推荐")
@RestController
@RequestMapping
@RequiredArgsConstructor
@Validated
public class DishController {

    private final DishService dishService;

    @Operation(summary = "热门菜品TOP10", description = "按收藏量降序取前10条，含食堂名称和档口名称")
    @GetMapping("/dishes/hot")
    public Result<List<DishVO>> getHotDishes() {
        List<DishVO> hotDishes = dishService.getHotDishes();
        return Result.success(hotDishes);
    }

    @Operation(summary = "菜品列表", description = "分页查询菜品，支持关键词搜索、食堂/档口筛选、价格区间、排序")
    @GetMapping("/dishes")
    public Result<?> listDishes(@ModelAttribute DishQueryReq req) {
        return Result.success(dishService.listDishes(req));
    }

    @Operation(summary = "菜品详情", description = "获取菜品详细信息，已登录用户会附带是否收藏/是否评价状态")
    @GetMapping("/dishes/{id}")
    public Result<?> getDishDetail(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        return Result.success(dishService.getDishDetail(id, userId));
    }

    @Operation(summary = "增加浏览量", description = "进入菜品详情页时调用")
    @PostMapping("/dishes/{id}/view")
    public Result<Void> addView(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        dishService.addViewCount(id, userId);
        return Result.success();
    }
}
