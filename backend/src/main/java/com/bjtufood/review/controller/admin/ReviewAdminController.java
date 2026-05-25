package com.bjtufood.review.controller.admin;

import com.bjtufood.common.result.Result;
import com.bjtufood.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评价审核控制器（系统管理员专用）
 * <p>
 * 系统管理员查看所有评价、标记隐藏/删除违规评价。
 * 敏感词在提交时已过滤，审核页面仅做二次确认。
 */
@Tag(name = "评价审核（系统管理员）", description = "评价列表查看、隐藏/显示、删除")
@RestController
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
public class ReviewAdminController {

    private final ReviewService reviewService;

    @Operation(summary = "所有评价列表", description = "查看所有评价（含已隐藏/已删除），敏感词高亮标记")
    @GetMapping
    public Result<?> listAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer isHidden,
            @RequestParam(required = false) Integer isDeleted) {
        // TODO: 调用 ReviewService.listAllForAdmin(page, pageSize, isHidden, isDeleted)
        return Result.success("评价列表");
    }

    @Operation(summary = "切换隐藏/显示", description = "隐藏后前端不再展示该评价，再次调用恢复显示")
    @PutMapping("/{id}/hide")
    public Result<Void> toggleHide(@PathVariable Long id) {
        // TODO: 调用 ReviewService.toggleHide(id)
        return Result.success();
    }

    @Operation(summary = "删除评价", description = "管理员删除评价（软删除）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteReview(@PathVariable Long id) {
        // TODO: 调用 ReviewService.deleteByAdmin(id)
        return Result.success();
    }
}
