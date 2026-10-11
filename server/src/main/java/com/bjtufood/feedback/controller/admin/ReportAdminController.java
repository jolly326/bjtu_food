package com.bjtufood.feedback.controller.admin;

import com.bjtufood.auth.support.SecurityUtil;
import com.bjtufood.common.result.AdminPageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.feedback.dto.ReportAdminVO;
import com.bjtufood.feedback.service.ReportService;
import com.bjtufood.moderation.dto.ModerationHandleReq;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评价举报管理接口（Web 后台，ADM）。
 * <p>
 * 举报是独立资源（`report` 表）：列表与处置各有专属端点，与意见反馈（{@code /admin/feedbacks}）互不混装。
 */
@Tag(name = "16. 后台举报处理", description = "管理员查看/处置用户举报，可联动隐藏被举报评价。需要管理员 token。")
@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class ReportAdminController {

    private final ReportService reportService;

    @Operation(summary = "举报列表", description = "ADM。分页，按处置状态筛选（pending/handled；不传 = 全部）"
            + "与按举报人筛选；排序固定 `createdAt DESC`。"
            + "出参补齐举报人昵称、举报原因中文名（含已停用）、被举报评价摘要与当前隐藏态"
            + "（决定「同时隐藏」复选是否置灰）。"
            + "**被举报评价已删除时下发 reviewDeleted=true**（摘要与可见性置空，举报行本身保留）。"
            + "测试示例：/admin/reports?page=1&pageSize=20&status=pending")
    @GetMapping
    public Result<AdminPageResult<ReportAdminVO>> list(
            @Parameter(description = "处置状态：pending=待处置 / handled=已处置；不传 = 全部；非法值 400")
            @RequestParam(required = false) String status,
            @Parameter(description = "举报人用户ID（可选）")
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(AdminPageResult.of(
                reportService.listForAdmin(status, userId, page, pageSize)));
    }

    @Operation(summary = "处置举报", description = "ADM。仅接受 JSON body（{replyTitle, replyContent, outcome, hideReview}）："
            + "replyTitle 回执短标题（可选，≤40 字，留空用缺省文案）；"
            + "replyContent 处置文案（可选，≤600 字；outcome=rejected 时**必填**，即为不采纳原因，纯空白 → 400）；"
            + "outcome=handled（受理，缺省）或 rejected（不予处理），非法值 400；"
            + "hideReview **仅受理时消费**：true 联动隐藏被举报评价（幂等：评价已隐藏 / 已删除时静默成功）。"
            + "处置动作 = 更新处置任务行 + 投递站内回执（同一事务）；已处置再处置 → 400；记录不存在 → 4001。")
    @PutMapping("/{id}")
    public Result<Void> handle(
            @Parameter(description = "举报ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "处置请求体 {replyTitle, replyContent, outcome, hideReview}；校验失败返回 400")
            @Valid @RequestBody ModerationHandleReq body) {
        reportService.handle(id, body, SecurityUtil.getCurrentAdminId());
        return Result.success();
    }
}
