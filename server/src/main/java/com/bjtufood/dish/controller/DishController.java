package com.bjtufood.dish.controller;

import com.bjtufood.common.annotation.RequireVerified;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.dish.dto.DishPublishReq;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.dish.dto.HotSearchVO;
import com.bjtufood.dish.dto.MyDishVO;
import com.bjtufood.dish.service.DishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "03. 菜品浏览", description = "公开菜品查询、热门菜品、菜品详情、浏览量记录。")
@RestController
@RequestMapping
@RequiredArgsConstructor
@Validated
public class DishController {

    private final DishService dishService;

    @Operation(summary = "热门菜品 TOP", description = "用途：首页热门推荐。按收藏数、评分等规则返回热门菜品；可选传 lat/lng 按用户位置距离加权（近食堂菜品优先，首页推荐联动定位）；可选传 limit 控制返回条数。")
    @GetMapping("/dishes/hot")
    public Result<List<DishVO>> getHotDishes(
            @Parameter(description = "用户纬度（GCJ-02，可选）", example = "39.9538")
            @RequestParam(required = false) BigDecimal lat,
            @Parameter(description = "用户经度（GCJ-02，可选）", example = "116.3354")
            @RequestParam(required = false) BigDecimal lng,
            @Parameter(description = "返回条数（可选，默认 10，前端首页热门列表可控制数量）", example = "10")
            @RequestParam(required = false) Integer limit) {
        return Result.success(dishService.getHotDishes(lat, lng, limit));
    }

    @Operation(summary = "今日上新菜品", description = "用途：首页今日上新板块。按创建时间降序返回最新菜品。")
    @GetMapping("/dishes/new")
    public Result<List<DishVO>> getNewDishes() {
        return Result.success(dishService.getNewDishes());
    }

    @Operation(summary = "限时活动菜品", description = "用途：首页限时活动板块。返回有活动特价的菜品。")
    @GetMapping("/dishes/promotions")
    public Result<List<DishVO>> getPromotionDishes() {
        return Result.success(dishService.getPromotionDishes());
    }

    @Operation(
            summary = "热搜榜单 TOP10",
            description = "用途：搜索/发现页热搜榜。一期限定：无真实搜索词埋点，基于菜品综合热度派生热门词条。公开接口。"
    )
    @GetMapping("/dishes/hot-search")
    public Result<List<HotSearchVO>> hotSearch() {
        return Result.success(dishService.hotSearch());
    }

    @Operation(
            summary = "新晋黑马 TOP10",
            description = "用途：搜索/发现页新晋榜。取近 14 天新上架且热度增速高的菜品。公开接口。"
    )
    @GetMapping("/dishes/rising")
    public Result<List<DishVO>> rising() {
        return Result.success(dishService.rising());
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
            summary = "猜你喜欢（推荐菜品）",
            description = """
                    用途：首页「猜你喜欢」板块。公开接口，无需登录；登录态个性化更强。
                    仅 approved 且上架菜品参与，按热度分降序；支持 excludeIds 排除已展示项。
                    测试示例：/dishes/recommend?page=1&pageSize=10&excludeIds=1,2
                    """
    )
    @GetMapping("/dishes/recommend")
    public Result<?> recommendDishes(
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "排除的菜品ID（逗号分隔）", example = "1,2")
            @RequestParam(required = false) String excludeIds) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        return Result.success(dishService.recommendDishes(page, pageSize, excludeIds, userId));
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

    // ==================== 学生端发布接口（STUDENT） ====================

    @Operation(
            summary = "学生发布菜品",
            description = "学生提交新菜品。后端强制写入 created_by=当前用户、audit_status=pending，等待后台审核。需已完成学号邮箱认证。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @RequireVerified
    @PostMapping("/dishes")
    public Result<Long> createDish(@Valid @RequestBody DishPublishReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(dishService.createStudentDish(req, userId));
    }

    @Operation(
            summary = "学生编辑 / 重新提交菜品",
            description = "仅本人发布的菜品可编辑；重提复用原记录，audit_status 重置为 pending、reject_reason 清空。需已完成学号邮箱认证。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @RequireVerified
    @PutMapping("/dishes/{id}")
    public Result<Void> updateDish(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody DishPublishReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        dishService.updateStudentDish(id, req, userId);
        return Result.success();
    }

    @Operation(
            summary = "我的发布列表",
            description = "返回当前学生提交的菜品（含审核状态与退回原因），可按 audit_status 过滤。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my/dishes")
    public Result<List<MyDishVO>> myDishes(
            @Parameter(description = "审核状态过滤：pending/approved/rejected", example = "pending")
            @RequestParam(required = false) String auditStatus) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(dishService.listMyDishes(userId, auditStatus));
    }

    @Operation(
            summary = "学生删除本人菜品",
            description = "仅 created_by 本人可删，返回 200/403/404。级联清理评价与清单项（favorite 模块已移除，不处理）。需已完成学号邮箱认证。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @RequireVerified
    @DeleteMapping("/dishes/{id}")
    public Result<Void> deleteMyDish(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        dishService.deleteMyDish(id, userId);
        return Result.success();
    }
}
