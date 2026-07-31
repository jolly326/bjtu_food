package com.bjtufood.review.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.review.dto.ReviewReq;
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
import org.springframework.web.bind.annotation.*;

@Tag(name = "05. 评价", description = "菜品评价列表、提交评价、修改评价、删除评价。提交/修改/删除需要登录。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "菜品评价列表", description = "用途：菜品详情页展示评价。只返回未隐藏评价。支持 sort=latest（默认）/useful。测试示例：/dishes/1/reviews?page=1&pageSize=20&sort=useful")
    @GetMapping("/dishes/{dishId}/reviews")
    public Result<?> listReviewsByDish(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long dishId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "排序：latest（最新，默认）/ useful（最有用的）", example = "latest")
            @RequestParam(defaultValue = "latest") String sort) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        return Result.success(reviewService.listByDishId(dishId, page, pageSize, sort, userId));
    }

    @Operation(summary = "菜品评价列表（契约路径）", description = "用途：与上方等价，遵循 spec §3.x.5 契约路径 /reviews?dishId=。只返回未隐藏评价。支持 sort=latest/useful。测试示例：/reviews?dishId=1&page=1&pageSize=20&sort=useful")
    @GetMapping("/reviews")
    public Result<?> listReviews(
            @Parameter(description = "菜品ID", example = "1")
            @RequestParam Long dishId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "排序：latest（最新，默认）/ useful（最有用的）", example = "latest")
            @RequestParam(defaultValue = "latest") String sort) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        return Result.success(reviewService.listByDishId(dishId, page, pageSize, sort, userId));
    }

    @Operation(
            summary = "我的评价列表",
            description = "用途：个人中心「我的评价」。返回当前登录用户自己提交的评价（不含被隐藏项），按时间倒序。测试示例：/my/reviews?page=1&pageSize=20",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/my/reviews")
    public Result<?> listMyReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(reviewService.listByUserId(userId, page, pageSize));
    }

    @Operation(
            summary = "提交评价",
            description = "用途：用户对菜品评分和评论。每个用户对同一菜品只能评价一次，提交后重算菜品评分。",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "dishId": 1,
                      "rating": 5,
                      "content": "味道不错，分量也足。",
                      "images": ["/images/seed/dishes/tomato-egg.jpg"]
                    }
                    """)))
    )
    @PostMapping("/reviews")
    public Result<Void> submitReview(@Valid @RequestBody ReviewReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        reviewService.submitReview(userId, req);
        return Result.success();
    }

    @Operation(
            summary = "修改自己的评价",
            description = "用途：修改当前用户自己的评价评分和文字内容。当前实现不修改图片。",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "dishId": 1,
                      "rating": 4,
                      "content": "重新评价：整体不错。",
                      "images": []
                    }
                    """)))
    )
    @PutMapping("/reviews/{id}")
    public Result<Void> updateReview(
            @Parameter(description = "评价ID", example = "1")
            @PathVariable Long id,
            @RequestBody ReviewReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        reviewService.updateReview(id, userId, req.getRating(), req.getContent());
        return Result.success();
    }

    @Operation(summary = "删除自己的评价", description = "用途：删除当前用户自己的评价，删除后重算菜品评分。", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/reviews/{id}")
    public Result<Void> deleteReview(
            @Parameter(description = "评价ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        reviewService.deleteReview(id, userId);
        return Result.success();
    }

    @Operation(summary = "删除本人评价（契约路径）", description = "用途：task-12.5 契约路径 DELETE /my/reviews/{id}，仅删除本人 userId 的评价，级联清理 useful 关联；返回 200/403/404。", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/my/reviews/{id}")
    public Result<Void> deleteMyReview(
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
    @PostMapping("/reviews/{id}/useful")
    public Result<UsefulResult> toggleUseful(
            @Parameter(description = "评价ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(reviewService.toggleUseful(userId, id));
    }
}
