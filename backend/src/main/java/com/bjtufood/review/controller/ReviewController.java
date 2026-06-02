package com.bjtufood.review.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.review.dto.ReviewReq;
import com.bjtufood.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评价控制器
 * <p>
 * 学生用户对菜品进行评价、编辑、删除。
 * 提交评价后发布事件，触发评分重算。
 */
@Tag(name = "评价管理", description = "提交评价、修改评价、删除评价、查看评价列表")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "获取菜品评价列表", description = "查看菜品的用户评价，按时间倒序（不含已删除/已隐藏）")
    @GetMapping("/dishes/{dishId}/reviews")
    public Result<?> listReviews(
            @PathVariable Long dishId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(reviewService.listByDishId(dishId, page, pageSize));
    }

    @Operation(summary = "提交评价", description = "对菜品进行评分和图文评价（每人每菜只能评价一次）")
    @PostMapping("/reviews")
    public Result<Void> submitReview(@Valid @RequestBody ReviewReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        reviewService.submitReview(userId, req);
        return Result.success();
    }

    @Operation(summary = "修改评价", description = "修改自己的评价内容（仅可修改评分和文字，不可修改图片）")
    @PutMapping("/reviews/{id}")
    public Result<Void> updateReview(
            @PathVariable Long id,
            @RequestBody ReviewReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        reviewService.updateReview(id, userId, req.getRating(), req.getContent());
        return Result.success();
    }

    @Operation(summary = "删除评价", description = "删除自己的评价（软删除）")
    @DeleteMapping("/reviews/{id}")
    public Result<Void> deleteReview(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        reviewService.deleteReview(id, userId);
        return Result.success();
    }
}
