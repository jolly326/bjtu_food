package com.bjtufood.feedback.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.annotation.AuditLog;
import com.bjtufood.common.constant.OperationLogConst;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 反馈管理接口（Web 后台，ADM）
 */
@Tag(name = "15. 后台反馈处理", description = "管理员查看/处理用户反馈。需要管理员 token。")
@RestController
@RequestMapping("/admin/feedbacks")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class FeedbackAdminController {

    private final FeedbackService feedbackService;

    @Operation(summary = "反馈列表", description = "ADM。按 status/type 过滤。")
    @GetMapping
    public Result<PageResult<FeedbackAdminVO>> list(
            @Parameter(description = "处理状态：pending/handled")
            @RequestParam(required = false) String status,
            @Parameter(description = "反馈类型：suggestion/error/other")
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        IPage<FeedbackAdminVO> result = feedbackService.listForAdmin(status, type, page, pageSize);
        return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
    }

    @Operation(summary = "处理反馈", description = "ADM。标记 handled + 写 reply/handled_at/handler_id，埋操作日志。")
    @AuditLog(action = OperationLogConst.ACTION_FEEDBACK_HANDLE, targetType = "feedback", targetId = "#id")
    @PutMapping("/{id}")
    public Result<Void> handle(
            @Parameter(description = "反馈ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "回复内容（可选）")
            @RequestParam(required = false) String reply) {
        Long handlerId = SecurityUtil.getCurrentUserId();
        feedbackService.handle(id, handlerId, reply);
        return Result.success();
    }
}
