package com.bjtufood.history.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.history.dto.ViewLogVO;
import com.bjtufood.history.service.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 浏览足迹接口（task-07，STU）
 */
@Tag(name = "07. 浏览足迹", description = "我的足迹列表/删除/清空。学生态。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @Operation(summary = "我的足迹列表", description = "STU。倒序，支持 targetType 过滤。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my/history")
    public Result<PageResult<ViewLogVO>> list(
            @Parameter(description = "浏览对象类型过滤：dish/stall/canteen/moment")
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = SecurityUtil.getCurrentUserId();
        IPage<ViewLogVO> result = historyService.listMyHistory(userId, targetType, page, pageSize);
        return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
    }

    @Operation(summary = "写入浏览足迹", description = "STU。在菜品/档口/食堂/动态详情页调用。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/my/history")
    public Result<Void> record(
            @Parameter(description = "浏览对象类型：dish/stall/canteen/moment", example = "dish")
            @RequestParam String targetType,
            @Parameter(description = "浏览对象ID", example = "1")
            @RequestParam Long targetId) {
        Long userId = SecurityUtil.getCurrentUserId();
        historyService.record(userId, targetType, targetId);
        return Result.success();
    }

    @Operation(summary = "删除单条足迹", description = "STU 归属校验。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @DeleteMapping("/my/history/{id}")
    public Result<Void> deleteOne(
            @Parameter(description = "足迹ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        historyService.deleteOne(userId, id);
        return Result.success();
    }

    @Operation(summary = "清空全部足迹", description = "STU。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @DeleteMapping("/my/history")
    public Result<Void> clearAll() {
        Long userId = SecurityUtil.getCurrentUserId();
        historyService.clearAll(userId);
        return Result.success();
    }
}
