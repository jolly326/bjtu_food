package com.bjtufood.content.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.content.dto.AuditVO;
import com.bjtufood.content.dto.AuditRejectReq;
import com.bjtufood.content.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * UGC 审核接口（Web 后台，仅 ADMIN）
 * <p>
 * 菜品 / 档口 / 食堂审核通过 / 退回；退回必填 reject_reason。
 */
@Tag(name = "14. 后台UGC审核", description = "系统管理员审核学生提交的菜品/档口/食堂。需要管理员 token。")
@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditService auditService;

    @Operation(summary = "审核列表", description = "用途：菜品审核/档口食堂审核模块。支持按 type(dish/stall/canteen) 与 status(pending/approved/rejected) 过滤。测试示例：/admin/audit?type=dish&status=pending&page=1&pageSize=10")
    @GetMapping
    public Result<PageResult<AuditVO>> listAudit(
            @Parameter(description = "审核对象类型：dish / stall / canteen", example = "dish")
            @RequestParam String type,
            @Parameter(description = "审核状态：pending/approved/rejected", example = "pending")
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        IPage<AuditVO> result = auditService.listAudit(type, status, page, pageSize);
        return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
    }

    @Operation(summary = "审核通过", description = "用途：通过置 audit_status=approved，入正式体系并展示。")
    @PostMapping("/{type}/{id}/approve")
    public Result<Void> approve(
            @Parameter(description = "审核对象类型：dish / stall / canteen", example = "dish")
            @PathVariable String type,
            @Parameter(description = "记录ID", example = "1")
            @PathVariable Long id) {
        auditService.approve(type, id);
        return Result.success();
    }

    @Operation(summary = "审核退回", description = "用途：退回置 audit_status=rejected 并回写 reject_reason（必填）。")
    @PostMapping("/{type}/{id}/reject")
    public Result<Void> reject(
            @Parameter(description = "审核对象类型：dish / stall / canteen", example = "dish")
            @PathVariable String type,
            @Parameter(description = "记录ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody AuditRejectReq req) {
        auditService.reject(type, id, req.getRejectReason());
        return Result.success();
    }

    @Operation(summary = "批量审核通过", description = "用途：多选通过后批量置 approved。")
    @PostMapping("/{type}/batch-approve")
    public Result<Void> batchApprove(
            @Parameter(description = "审核对象类型：dish / stall / canteen", example = "dish")
            @PathVariable String type,
            @RequestBody List<Long> ids) {
        auditService.batchApprove(type, ids);
        return Result.success();
    }

    @Operation(summary = "批量审核退回", description = "用途：多选退回，批量置 rejected 并写原因（必填）。")
    @PostMapping("/{type}/batch-reject")
    public Result<Void> batchReject(
            @Parameter(description = "审核对象类型：dish / stall / canteen", example = "dish")
            @PathVariable String type,
            @Valid @RequestBody AuditRejectReq req) {
        auditService.batchReject(type, req.getIds(), req.getRejectReason());
        return Result.success();
    }
}
