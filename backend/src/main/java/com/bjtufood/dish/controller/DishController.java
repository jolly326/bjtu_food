package com.bjtufood.dish.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.service.DishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
public class DishController {

    private final DishService dishService;

    @Operation(summary = "菜品列表", description = "分页查询菜品，支持关键词搜索、食堂/档口筛选、价格区间、排序")
    @GetMapping("/dishes")
    public Result<?> listDishes(DishQueryReq req) {
        // TODO: 调用 DishService.listDishes(req)
        // 默认排序：综合热度降序
        // 只返回上架且未删除的菜品
        return Result.success("菜品列表");
    }

    @Operation(summary = "热门菜品TOP10", description = "按收藏量降序取前10条")
    @GetMapping("/dishes/hot")
    public Result<?> getHotDishes() {
        // TODO: 调用 DishService.getHotDishes()
        return Result.success("热门菜品");
    }

    @Operation(summary = "菜品详情", description = "获取菜品详细信息，已登录用户会附带是否收藏/是否评价状态")
    @GetMapping("/dishes/{id}")
    public Result<?> getDishDetail(@PathVariable Long id) {
        // TODO: 从 SecurityContext 获取当前 userId（未登录为 null）
        // 调用 DishService.getDishDetail(id, userId)
        return Result.success("菜品详情");
    }

    @Operation(summary = "增加浏览量", description = "进入菜品详情页时调用，同一用户同一菜品5分钟内只计1次")
    @PostMapping("/dishes/{id}/view")
    public Result<Void> addView(@PathVariable Long id) {
        // TODO: 获取当前 userId，调用 DishService.addViewCount(id, userId)
        return Result.success();
    }
}
