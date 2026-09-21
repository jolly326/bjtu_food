package com.bjtufood.dish.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.dish.dto.HotSearchVO;
import com.bjtufood.dish.service.DishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "03. 菜品浏览", description = "公开菜品分页查询、热搜榜单、菜品详情、浏览量记录。")
@RestController
@RequestMapping
@RequiredArgsConstructor
@Validated
public class DishController {

    private final DishService dishService;

    @Operation(
            summary = "热搜榜单 TOP10",
            description = "用途：搜索/发现页热搜榜。一期限定：无真实搜索词埋点，基于菜品综合热度派生热门词条。公开接口。"
    )
    @GetMapping("/dishes/hot-search")
    public Result<List<HotSearchVO>> hotSearch() {
        return Result.success(dishService.hotSearch());
    }

    @Operation(
            summary = "菜品分页查询",
            description = """
                    用途：菜品列表页、搜索页、筛选页。
                    测试示例：/dishes?page=1&pageSize=10&keyword=牛肉
                    常用参数：keyword、canteenId、mealType、minPrice、maxPrice（排序恒为服务端热度倒序，无排序入口）。
                    """
    )
    @GetMapping("/dishes")
    public Result<PageResult<DishVO>> listDishes(@ModelAttribute DishQueryReq req) {
        IPage<DishVO> result = dishService.listDishes(req);
        // current/size 为 Service 内 PageUtil.normalize 后的实际生效值，契约要求以归一化值为准
        return Result.success(PageResult.of(result.getRecords(), result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize()));
    }

    @Operation(
            summary = "菜品大类字典",
            description = """
                    用途：首页横向大类标签栏数据源（2026-09-21 §7.34）。
                    只下发「当前有在售菜品」的大类（空类自动隐藏）；文案与顺序由后端 MealTypeConst 唯一定义，
                    端上不得维护任何标签中文映射。公开接口。
                    测试示例：/dishes/meal-types
                    """
    )
    @GetMapping("/dishes/meal-types")
    public Result<List<com.bjtufood.dish.dto.MealTypeVO>> listMealTypes() {
        return Result.success(dishService.listMealTypes());
    }

    @Operation(
            summary = "菜品详情",
            description = """
                    用途：菜品详情页。
                    未登录可访问；登录态与游客态返回结构一致（原 hasReviewed 已下线）。
                    测试示例：/dishes/1
                    """
    )
    @GetMapping("/dishes/{id}")
    public Result<?> getDishDetail(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id) {
        // 详情不依赖登录态（hasReviewed 已下线），故不再解析当前用户；路径与响应结构零变化
        return Result.success(dishService.getDishDetail(id));
    }

    @Operation(
            summary = "增加浏览量",
            description = """
                    用途：进入菜品详情页时调用一次。需要登录，用于记录真实用户浏览行为。
                    去重口径：同一用户对同一菜品每天（自然日，Asia/Shanghai）只计 1 次；
                    当日重复调用幂等返回成功（code=200），不自增 view_count、不重复写浏览记录。
                    测试示例：/dishes/1/views
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/dishes/{id}/views")
    public Result<Void> addView(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        dishService.addViewCount(id, userId);
        return Result.success();
    }
}
