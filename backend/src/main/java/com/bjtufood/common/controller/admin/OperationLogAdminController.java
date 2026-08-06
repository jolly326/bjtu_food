package com.bjtufood.common.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.dto.OperationLogVO;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志接口（Web 后台，ADM，只读）
 */
@Tag(name = "17. 后台操作日志", description = "操作日志只读查询。需要管理员 token。")
@RestController
@RequestMapping("/admin/operation-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class OperationLogAdminController {

    private final OperationLogService operationLogService;

    @Operation(summary = "操作日志列表", description = "ADM。支持 adminId/action/targetType/startAt/endAt 过滤。")
    @GetMapping
    public Result<PageResult<OperationLogVO>> list(
            @Parameter(description = "操作管理员ID")
            @RequestParam(required = false) Long adminId,
            @Parameter(description = "动作标识：audit_approve/audit_reject/moment_hide/moment_delete/feedback_handle")
            @RequestParam(required = false) String action,
            @Parameter(description = "操作对象类型：moment/dish/stall/canteen/feedback/review")
            @RequestParam(required = false) String targetType,
            @Parameter(description = "起始时间 yyyy-MM-dd HH:mm:ss")
            @RequestParam(required = false) String startAt,
            @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss")
            @RequestParam(required = false) String endAt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        IPage<OperationLogVO> result = operationLogService.listLogs(adminId, action, targetType, startAt, endAt, page, pageSize);
        return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
    }
}
