package com.bjtufood.feedback.controller.admin;

import com.bjtufood.auth.support.SecurityUtil;
import com.bjtufood.common.result.AdminPageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.service.FeedbackService;
import com.bjtufood.moderation.dto.ModerationHandleReq;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 意见反馈管理接口（Web 后台，ADM）。
 * <p>
 * 举报是独立资源（列表与处置见 {@code ReportAdminController}），故本端点是**单一资源**的列表与处置：
 * 没有板块分流参数，筛选维度只有「处置状态 / 类型 / 提交人 / 关键词」。
 * 处置结果（状态 / 结论 / 处置文案 / 处置人 / 处置时间）来自处置任务表。
 */
@Tag(name = "15. 后台反馈处理", description = "管理员查看/处置用户意见反馈。需要管理员 token。")
@RestController
@RequestMapping("/admin/feedbacks")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class FeedbackAdminController {

    private final FeedbackService feedbackService;

    @Operation(summary = "反馈列表", description = "ADM。分页，按处置状态筛选（pending/handled；不传 = 全部）、"
            + "按反馈类型（optionId）/提交人筛选，关键词匹配反馈正文**或**处置文案；排序固定 `createdAt DESC`。"
            + "出参补齐提交人昵称、类型中文名（含已停用）与处置结果（含处理人）。"
            + "测试示例：/admin/feedbacks?page=1&pageSize=20&status=pending")
    @GetMapping
    public Result<AdminPageResult<FeedbackAdminVO>> list(
            @Parameter(description = "处置状态：pending=待处置 / handled=已处置；不传 = 全部；非法值 400")
            @RequestParam(required = false) String status,
            @Parameter(description = "反馈类型ID（feedback_option 字典项；不传 = 全部）")
            @RequestParam(required = false) Long optionId,
            @Parameter(description = "提交用户ID（可选，用户行为聚合用）")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "关键词（可选，匹配反馈正文或处置文案）")
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(AdminPageResult.of(
                feedbackService.listForAdmin(status, optionId, userId, keyword, page, pageSize)));
    }

    @Operation(summary = "处置反馈", description = "ADM。仅接受 JSON body（{replyTitle, replyContent, outcome}）："
            + "replyTitle 回执短标题（可选，≤40 字，留空用缺省文案）；"
            + "replyContent 处置文案（可选，≤600 字；outcome=rejected 时**必填**，即为不采纳原因，纯空白 → 400）；"
            + "outcome=handled（通过/已处理，缺省）或 rejected（不采纳/退回），非法值 400。"
            + "处置动作 = 更新处置任务行 + 投递站内回执（同一事务）；已处置再处置 → 400；记录不存在 → 4001。")
    @PutMapping("/{id}")
    public Result<Void> handle(
            @Parameter(description = "反馈ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "处置请求体 {replyTitle, replyContent, outcome}；校验失败返回 400")
            @Valid @RequestBody ModerationHandleReq body) {
        feedbackService.handle(id, body, SecurityUtil.getCurrentAdminId());
        return Result.success();
    }
}
