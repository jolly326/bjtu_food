package com.bjtufood.feedback.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.annotation.AuditLog;
import com.bjtufood.common.constant.OperationLogConst;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    @Operation(summary = "反馈列表", description = "ADM。按 status/type/secState/userId/keyword 过滤；不传 status 则返回全部状态（含已处理，供回看）；secState=review 捞内容安全待人工复核队列。")
    @GetMapping
    public Result<PageResult<FeedbackAdminVO>> list(
            @Parameter(description = "处理状态：pending/handled")
            @RequestParam(required = false) String status,
            @Parameter(description = "反馈类型：suggestion/add/error/bug/report/other")
            @RequestParam(required = false) String type,
            @Parameter(description = "提交用户ID（可选，用户行为聚合用）")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "内容安全状态：pass/review/rejected（可选，复核队列用）", example = "review")
            @RequestParam(required = false) String secState,
            @Parameter(description = "关键词（可选，对反馈内容 / 管理员回复模糊匹配）")
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        IPage<FeedbackAdminVO> result = feedbackService.listForAdmin(status, type, userId, secState, keyword, page, pageSize);
        return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
    }

    @Operation(summary = "处理反馈", description = "ADM。标记 handled + 写 reply/handled_at，埋操作日志。reply 支持 JSON body（{reply:...}）或 query 参数两种传法，body 优先。")
    @AuditLog(action = OperationLogConst.ACTION_FEEDBACK_HANDLE, targetType = "feedback", targetId = "#id")
    @PutMapping("/{id}")
    public Result<Void> handle(
            @Parameter(description = "反馈ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "回复内容（可选），JSON body {reply:...} 或 query 参数")
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String reply) {
        String replyText = (body != null && body.get("reply") != null && !body.get("reply").isBlank())
                ? body.get("reply") : reply;
        feedbackService.handle(id, replyText);
        return Result.success();
    }
}
