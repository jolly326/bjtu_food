package com.bjtufood.moment.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.annotation.AuditLog;
import com.bjtufood.common.constant.OperationLogConst;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.moment.dto.MomentVO;
import com.bjtufood.moment.service.MomentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 动态管理接口（Web 后台，ADM，W5）
 */
@Tag(name = "16. 后台动态管理", description = "动态列表 / 强制下架 / 删除动态。需要管理员 token。")
@RestController
@RequestMapping("/admin/moments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MomentAdminController {

    private final MomentService momentService;

    @Operation(summary = "动态管理列表", description = "ADM。返回含全部状态的动态（approved+status=0、已下架 status=1、pending 等），供管理台分段展示；支持 status/auditStatus 过滤。")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Result<PageResult<MomentVO>> list(
            @Parameter(description = "下架状态：0=正常 1=下架（可选）")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "审核状态：pending/approved/rejected（可选）")
            @RequestParam(required = false) String auditStatus,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") int pageSize) {
        IPage<MomentVO> result = momentService.adminList(status, auditStatus, page, pageSize);
        return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
    }

    @Operation(summary = "强制下架动态", description = "ADM。status=1（区别于审核态），埋操作日志。")
    @AuditLog(action = OperationLogConst.ACTION_MOMENT_HIDE, targetType = "moment", targetId = "#id")
    @PutMapping("/{id}/hide")
    public Result<Void> hide(
            @Parameter(description = "动态ID", example = "1")
            @PathVariable Long id) {
        momentService.hide(id);
        return Result.success();
    }

    @Operation(summary = "删除动态", description = "ADM。物理删除（二次确认由前端），连带评论/通知，埋操作日志。")
    @AuditLog(action = OperationLogConst.ACTION_MOMENT_DELETE, targetType = "moment", targetId = "#id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "动态ID", example = "1")
            @PathVariable Long id) {
        momentService.adminDelete(id);
        return Result.success();
    }
}
