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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "10. 后台评价审核", description = "系统管理员查看、隐藏、删除评价。需要管理员 token。")
@RestController
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ReviewAdminController {

    private final ReviewService reviewService;

    @Operation(summary = "全部评价列表", description = "用途：后台查看所有评价，支持按 isHidden/userId 筛选。测试示例：/admin/reviews?page=1&pageSize=10&isHidden=0")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping
    public Result<?> listAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer isHidden,
            @Parameter(description = "提交用户ID（可选，用户行为聚合用）")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "评价正文关键词（可选，模糊匹配）")
            @RequestParam(required = false) String keyword) {
        return Result.success(reviewService.listAllForAdmin(page, pageSize, isHidden, userId, keyword));
    }

    @Operation(summary = "设置评价隐藏/显示", description = "用途：显式设置评价隐藏状态（hidden=true 隐藏，false 恢复显示），避免 toggle 语义不确定。隐藏后公开评价列表不再展示。")
    @AuditLog(action = OperationLogConst.ACTION_REVIEW_HIDE, targetType = "review", targetId = "#id")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}/hide")
    public Result<Void> setHidden(
            @Parameter(description = "评价ID", example = "1")
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        boolean hidden = body != null && Boolean.TRUE.equals(body.get("hidden"));
        reviewService.setHidden(id, hidden);
        return Result.success();
    }

    @Operation(summary = "管理员删除评价", description = "用途：管理员删除评价。当前实现为物理删除，并触发菜品评分重算。")
    @AuditLog(action = OperationLogConst.ACTION_REVIEW_DELETE, targetType = "review", targetId = "#id")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> deleteReview(
            @Parameter(description = "评价ID", example = "1")
            @PathVariable Long id) {
        reviewService.deleteByAdmin(id);
        return Result.success();
    }
}
