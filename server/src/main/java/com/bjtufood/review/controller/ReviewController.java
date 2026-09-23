package com.bjtufood.review.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.annotation.RequireVerified;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
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
 * 评价与浏览端点。
 * <p>
 * RESTful 子资源路径（2026-09-20 拍板）：
 * <ul>
 *   <li>评价列表 {@code GET /dishes/{id}/reviews}；</li>
 *   <li>发表评价 {@code POST /dishes/{id}/reviews}（请求体不再携带菜品 ID，归属由路径锁定）；</li>
 *   <li>重新评价（覆盖式）{@code PUT /reviews/{id}}；</li>
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
            summary = "菜品评价列表（时间倒序）",
            description = """
                    用途：菜品详情页评价区。菜品归属由路径表达，分页与筛选经查询串传递。
                    排序唯一为发表时间倒序，不提供排序参数。
                    hasImage=1 时只返回带图评价，total 按该口径统计；缺省或 0 不过滤。
                    只返回未隐藏（is_hidden=0）的评价。
                    测试示例：/dishes/1/reviews?page=1&pageSize=20
                    """)
    @GetMapping("/dishes/{id}/reviews")
    public Result<PageResult<ReviewVO>> listReviews(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "只看有图：1=仅带图评价；缺省/0=不过滤", example = "1")
            @RequestParam(required = false) Integer hasImage) {
        return Result.success(toPageResult(reviewService.listByDishId(id, page, pageSize, hasImage)));
    }

    /**
     * IPage → PageResult 统一转换：集中填充契约字段 records/total/page/pageSize。
     * <p>
     * current/size 为 Service 内 PageUtil.normalize 后的实际生效值，故 page/pageSize 取之，
     * 而非 Controller 原始入参，避免越界/超限入参污染响应。
     */
    private <T> PageResult<T> toPageResult(IPage<T> result) {
        return PageResult.of(result.getRecords(), result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    @Operation(summary = "我的评价列表", description = "STU（需邮箱认证）。返回当前用户本人的评价（MyReviewVO：本人视角 11 字段 = 公开 8 + dishId/dishName/isHidden，2026-09-23 R9 拆类），按发表时间倒序。可选 dishId 按菜品过滤（详情页判定「我是否已评价」）。测试示例：/my/reviews?page=1&pageSize=20&dishId=1", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @RequireVerified
    @GetMapping("/my/reviews")
    public Result<PageResult<MyReviewVO>> listMyReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "菜品ID（可选，仅返回当前用户对该菜品的评价）", example = "1")
            @RequestParam(required = false) Long dishId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(toPageResult(reviewService.listByUserId(userId, page, pageSize, dishId)));
    }

    @Operation(
            summary = "提交评价",
            description = "用途：用户对菜品评分和评论。菜品归属由路径锁定，请求体不含菜品 ID。每个用户对同一菜品只能评价一次，提交后重算菜品评分。需已完成学号邮箱认证。",
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
    public Result<Void> submitReview(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody ReviewReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        reviewService.submitReview(userId, id, req);
        return Result.success();
    }

    @Operation(
            summary = "重新评价（覆盖式）",
            description = "用途：作者本人修改自己的评价。覆盖同一行（评分/文字/配图），发表时间刷新为当前（时间倒序列表置顶），隐藏标记重置为未隐藏，并重算菜品评分。需已完成学号邮箱认证；非作者 403。不限次数。",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "rating": 4,
                      "content": "重新评一次：味道还行，就是有点咸。"
                    }
                    """)))
    )
    @RequireVerified
    @PutMapping("/reviews/{id}")
    public Result<Void> updateReview(
            @Parameter(description = "评价ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody ReviewReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        reviewService.updateReview(id, userId, req);
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

}
