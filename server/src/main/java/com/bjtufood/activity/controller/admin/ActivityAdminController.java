package com.bjtufood.activity.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.activity.entity.Activity;
import com.bjtufood.activity.service.ActivityService;
import com.bjtufood.common.annotation.AuditLog;
import com.bjtufood.common.constant.OperationLogConst;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台活动管理（最新活动/公众号文章卡片增删改查）
 * <p>
 * 与用户端公开接口 {@code GET /activities} 互补：本模块管理全部活动（含 disabled）。
 */
@Tag(name = "21. 后台活动管理", description = "ADM。最新活动（公众号文章卡片）管理。需要管理员 token。")
@RestController
@RequestMapping("/admin/activities")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ActivityAdminController {

    private final ActivityService activityService;

    @Operation(summary = "活动列表", description = "ADM。分页查询，支持关键词/状态过滤，按 sort_order 升序、created_at 降序。")
    @GetMapping
    public Result<PageResult<Activity>> list(
            @Parameter(description = "关键词（标题/摘要模糊匹配）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "展示状态：enabled/disabled")
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        IPage<Activity> result = activityService.listForAdmin(keyword, status, page, pageSize);
        return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
    }

    @Operation(summary = "新增活动", description = "ADM。创建公众号文章卡片。")
    @AuditLog(action = OperationLogConst.ACTION_ACTIVITY_CREATE, targetType = "activity", targetId = "#result.id")
    @PostMapping
    public Result<Activity> create(@RequestBody Activity activity) {
        return Result.success(activityService.create(activity));
    }

    @Operation(summary = "编辑活动", description = "ADM。更新标题/摘要/封面/文章链接/排序/状态。")
    @AuditLog(action = OperationLogConst.ACTION_ACTIVITY_UPDATE, targetType = "activity", targetId = "#id")
    @PutMapping("/{id}")
    public Result<Void> update(
            @Parameter(description = "活动ID", example = "1")
            @PathVariable Long id,
            @RequestBody Activity activity) {
        activityService.update(id, activity);
        return Result.success();
    }

    @Operation(summary = "删除活动", description = "ADM。删除活动。")
    @AuditLog(action = OperationLogConst.ACTION_ACTIVITY_DELETE, targetType = "activity", targetId = "#id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "活动ID", example = "1")
            @PathVariable Long id) {
        activityService.delete(id);
        return Result.success();
    }
}
