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
                    常用参数：keyword、canteenId、stallId、tag、minPrice、maxPrice、sortBy、sortOrder。
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
            summary = "菜品详情",
            description = """
                    用途：菜品详情页。
                    未登录可访问；如果已登录并携带 token，会额外返回 hasReviewed。
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
