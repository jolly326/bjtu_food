package com.bjtufood.review.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.annotation.RequireVerified;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.review.dto.ReviewReq;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.dto.UsefulResult;
import com.bjtufood.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "05. 评价", description = "菜品评价列表、提交评价、修改评价、删除评价。提交/修改/删除需要登录。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "评价列表（契约路径）", description = "用途：遵循 spec §3.x.5 契约路径 /reviews?dishId=。支持按维度查询评价：dishId（菜品）、stallId（档口）、canteenId（食堂），三者至多传其一，都不传默认按 dishId 维度但 dishId 必填。只返回未隐藏评价。排序 sort=useful（默认，按有用数置顶）/latest。测试示例：/reviews?stallId=1&page=1&pageSize=20&sort=latest")
    @GetMapping("/reviews")
    public Result<PageResult<ReviewVO>> listReviews(
            @Parameter(description = "菜品ID（与 stallId / canteenId 至多传其一）", example = "1")
            @RequestParam(required = false) Long dishId,
            @Parameter(description = "档口ID（按档口查评价）", example = "1")
            @RequestParam(required = false) Long stallId,
            @Parameter(description = "食堂ID（按食堂查评价）", example = "1")
            @RequestParam(required = false) Long canteenId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "排序：useful（最有用的，默认）/ latest（最新）", example = "useful")
            @RequestParam(defaultValue = "useful") String sort) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        if (stallId != null) {
            return Result.success(toPageResult(reviewService.listByStallId(stallId, page, pageSize, sort, userId)));
        }
        if (canteenId != null) {
            return Result.success(toPageResult(reviewService.listByCanteenId(canteenId, page, pageSize, sort, userId)));
        }
        if (dishId == null) {
            throw new com.bjtufood.common.exception.BusinessException("dishId、stallId、canteenId 至少传入其一");
        }
        return Result.success(toPageResult(reviewService.listByDishId(dishId, page, pageSize, sort, userId)));
    }

    /**
     * IPage → PageResult 统一转换：集中填充契约字段 records/total/page/pageSize。
     * <p>
     * current/size 为 Service 内 PageUtil.normalize 后的实际生效值，故 page/pageSize 取之，
     * 而非 Controller 原始入参，避免越界/超限入参污染响应。
     */
    private PageResult<ReviewVO> toPageResult(IPage<ReviewVO> result) {
        return PageResult.of(result.getRecords(), result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    @Operation(summary = "我的评价列表", description = "STU（需邮箱认证）。返回当前用户本人的评价，按发表时间倒序，含菜品名 dishName。测试示例：/my/reviews?page=1&pageSize=20", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @RequireVerified
    @GetMapping("/my/reviews")
    public Result<PageResult<ReviewVO>> listMyReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(toPageResult(reviewService.listByUserId(userId, page, pageSize)));
    }

    @Operation(
            summary = "提交评价",
            description = "用途：用户对菜品评分和评论。每个用户对同一菜品只能评价一次，提交后重算菜品评分。需已完成学号邮箱认证。",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "dishId": 1,
                      "rating": 5,
                      "content": "味道不错，分量也足。"
                    }
                    """)))
    )
    @RequireVerified
    @PostMapping("/reviews")
    public Result<Void> submitReview(@Valid @RequestBody ReviewReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        reviewService.submitReview(userId, req);
        return Result.success();
    }

    @Operation(summary = "删除自己的评价", description = "用途：删除当前用户自己的评价，删除后重算菜品评分。需已完成学号邮箱认证。", security = @SecurityRequirement(name = "bearerAuth"))
    @RequireVerified
    @DeleteMapping("/reviews/{id}")
    public Result<Void> deleteReview(
            @Parameter(description = "评价ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        reviewService.deleteReview(id, userId);
        return Result.success();
    }

    @Operation(
            summary = "评价「有用」切换（幂等）",
            description = "用途：用户对评价标记/取消「有用」。未标记→标记并返回 useful=true；已标记→取消并返回 useful=false。重复点击即取消，不抛错。每人每条评价一票。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @RequireVerified
    @PostMapping("/reviews/{id}/useful")
    public Result<UsefulResult> toggleUseful(
            @Parameter(description = "评价ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(reviewService.toggleUseful(userId, id));
    }

}
