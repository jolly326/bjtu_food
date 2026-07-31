package com.bjtufood.apply.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.apply.dto.ApplyHandleReq;
import com.bjtufood.apply.dto.ApplyVO;
import com.bjtufood.apply.service.ApplyService;
import com.bjtufood.common.annotation.AuditLog;
import com.bjtufood.common.constant.OperationLogConst;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 实体贡献审核（管理端，task-12.1）
 */
@Tag(name = "16. 实体贡献审核", description = "审核菜品/档口/食堂的新增/下架/变更申请。ADM。")
@RestController
@RequestMapping("/admin/apply")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ApplyAdminController {

    private final ApplyService applyService;

    @Operation(summary = "申请审核列表", description = "ADM。按 status/entityType/applyType 过滤。")
    @GetMapping
    public Result<PageResult<ApplyVO>> list(
            @Parameter(description = "审核状态：pending/approved/rejected")
            @RequestParam(required = false) String status,
            @Parameter(description = "实体类型：DISH/STALL/CANTEEN")
            @RequestParam(required = false) String entityType,
            @Parameter(description = "申请类型：NEW/CLOSE/CHANGE")
            @RequestParam(required = false) String applyType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        IPage<ApplyVO> result = applyService.adminList(status, entityType, applyType, page, pageSize);
        return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
    }

    @Operation(summary = "审核通过", description = "ADM。触发副作用（新增写实体/下架置 off/变更写回字段）。", security = @SecurityRequirement(name = "bearerAuth"))
    @AuditLog(action = OperationLogConst.ACTION_APPLY_APPROVE, targetType = "apply", targetId = "#id")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(
            @Parameter(description = "申请ID", example = "1")
            @PathVariable Long id) {
        Long adminId = SecurityUtil.getCurrentUserId();
        applyService.approve(id, adminId);
        return Result.success();
    }

    @Operation(summary = "审核退回", description = "ADM。rejectReason 必填。", security = @SecurityRequirement(name = "bearerAuth"))
    @AuditLog(action = OperationLogConst.ACTION_APPLY_REJECT, targetType = "apply", targetId = "#id")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(
            @Parameter(description = "申请ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody ApplyHandleReq req) {
        Long adminId = SecurityUtil.getCurrentUserId();
        applyService.reject(id, adminId, req);
        return Result.success();
    }
}
