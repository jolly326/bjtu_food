package com.bjtufood.review.controller;

import com.bjtufood.common.annotation.RequireVerified;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.auth.support.SecurityUtil;
import com.bjtufood.review.dto.ReviewCreatedVO;
import com.bjtufood.review.dto.ReviewReq;
import com.bjtufood.review.dto.MyReviewVO;
import com.bjtufood.review.dto.ReviewVO;
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

/**
 * 评价端点（RESTful 子资源路径）。
 * <ul>
 *   <li>评价列表 {@code GET /dishes/{id}/reviews}；</li>
 *   <li>发表评价 {@code POST /dishes/{id}/reviews}（请求体不含菜品 ID，归属由路径锁定；**重复提交即覆盖式重评**，无需独立端点）；</li>
 *   <li>删除本人评价 {@code DELETE /reviews/{id}}；</li>
 *   <li>我的评价 {@code GET /my/reviews}（支持 dishId 过滤）。</li>
 * </ul>
 */
@Tag(name = "05. 评价", description = "菜品评价列表、提交/重新评价、删除评价。提交/重新评价/删除需要登录且已邮箱认证。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(
            summary = "菜品评价列表（时间倒序，可按星级筛选）",
            description = """
                    用途：菜品详情页评价区。菜品归属由路径表达，分页与筛选经查询串传递。
                    排序唯一为发表时间倒序，不提供排序参数。
                    可选 rating 按星级筛选（1~5 白名单，非法值 400 不静默降级；不传 = 全部）。
                    🔴 筛选为服务端过滤且参与分页 ⇒ 端上切换筛选须重置 page=1。
                    只返回未隐藏（is_hidden=0）的评价。
                    测试示例：/dishes/1/reviews?page=1&pageSize=20 / ?rating=5
                    """)
    @GetMapping("/dishes/{id}/reviews")
    public Result<PageResult<ReviewVO>> listReviews(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "按星级筛选（1~5；不传 = 全部）", example = "5")
            @RequestParam(required = false) Integer rating) {
        return Result.success(PageResult.of(reviewService.listByDishId(id, page, pageSize, rating)));
    }

    @Operation(summary = "我的评价列表", description = "STU（需邮箱认证）。返回当前用户本人的评价（MyReviewVO：本人视角 7 字段 = 公开 5（不含 userId/userNickname/userAvatar）+ dishId/dishName，与公开视角分型），按发表时间倒序。可选 dishId 按菜品过滤（详情页判定「我是否已评价」）。测试示例：/my/reviews?page=1&pageSize=20&dishId=1", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @RequireVerified
    @GetMapping("/my/reviews")
    public Result<PageResult<MyReviewVO>> listMyReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "菜品ID（可选，仅返回当前用户对该菜品的评价）", example = "1")
            @RequestParam(required = false) Long dishId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(PageResult.of(reviewService.listByUserId(userId, page, pageSize, dishId)));
    }

    @Operation(
            summary = "提交评价",
            description = "用途：用户对菜品评分和评论。菜品归属由路径锁定，请求体不含菜品 ID。**同一用户对同一菜品重复提交 = 覆盖旧评价**（2026-09-30 简化：不再返回「您已评价过该菜品」），提交后重算菜品评分。需已完成学号邮箱认证。成功返回评价 ID（data.id）。",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "rating": 5,
                      "content": "味道不错，分量也足。"
                    }
                    """)))
    )
    @RequireVerified
    @PostMapping("/dishes/{id}/reviews")
    public Result<ReviewCreatedVO> submitReview(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody ReviewReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(new ReviewCreatedVO(reviewService.submitReview(userId, id, req)));
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

}
