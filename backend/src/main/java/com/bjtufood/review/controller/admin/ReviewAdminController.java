package com.bjtufood.review.controller.admin;

import com.bjtufood.common.annotation.AuditLog;
import com.bjtufood.common.constant.OperationLogConst;
import com.bjtufood.common.result.Result;
import com.bjtufood.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "10. 后台评价审核", description = "系统管理员查看、隐藏、删除评价。需要管理员 token。")
@RestController
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ReviewAdminController {

    private final ReviewService reviewService;

    @Operation(summary = "全部评价列表", description = "用途：后台查看所有评价，支持按 isHidden 筛选。测试示例：/admin/reviews?page=1&pageSize=10&isHidden=0")
    @GetMapping
    public Result<?> listAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer isHidden,
            @RequestParam(required = false) Integer isDeleted) {
        return Result.success(reviewService.listAllForAdmin(page, pageSize, isHidden, isDeleted));
    }

    @Operation(summary = "切换隐藏/显示评价", description = "用途：隐藏违规评价；再次调用可恢复显示。隐藏后公开评价列表不再展示。")
    @AuditLog(action = OperationLogConst.ACTION_REVIEW_HIDE, targetType = "review", targetId = "#id")
    @PutMapping("/{id}/hide")
    public Result<Void> toggleHide(
            @Parameter(description = "评价ID", example = "1")
            @PathVariable Long id) {
        reviewService.toggleHide(id);
        return Result.success();
    }

    @Operation(summary = "管理员删除评价", description = "用途：管理员删除评价。当前实现为物理删除，并触发菜品评分重算。")
    @AuditLog(action = OperationLogConst.ACTION_REVIEW_DELETE, targetType = "review", targetId = "#id")
    @DeleteMapping("/{id}")
    public Result<Void> deleteReview(
            @Parameter(description = "评价ID", example = "1")
            @PathVariable Long id) {
        reviewService.deleteByAdmin(id);
        return Result.success();
    }
}
