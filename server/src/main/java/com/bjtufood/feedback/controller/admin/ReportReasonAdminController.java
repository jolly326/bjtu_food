package com.bjtufood.feedback.controller.admin;

import com.bjtufood.common.dto.SortItemsReq;
import com.bjtufood.common.result.Result;
import com.bjtufood.feedback.dto.ReportReasonAdminVO;
import com.bjtufood.feedback.dto.ReportReasonRenameReq;
import com.bjtufood.feedback.dto.ReportReasonSaveReq;
import com.bjtufood.feedback.dto.ReportReasonStatusReq;
import com.bjtufood.feedback.service.ReportReasonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A7 举报原因管理（管理端）。
 *
 * <p>契约真源：docs/api/web/report-reasons.md。
 * <p><b>没有「改机器值」的端点</b> —— `value` 是历史举报的数据锚点，在用后不可改；要改就停用旧值、新建一个。
 * <p>公开只读端点仍是 {@code GET /report-reasons}（`FeedbackController`），出参结构不变（只下发启用项）。
 */
@Tag(name = "12. 后台举报原因管理", description = "管理员维护举报弹层的原因字典：列表（含已停用）/ 新增 / 改名 / 启停 / 排序 / 删除。"
        + "不变量：删除受引用约束、至少保留 1 条启用、启用 ≤8 条。需要管理员 token。")
@RestController
@RequestMapping("/admin/report-reasons")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class ReportReasonAdminController {

    private final ReportReasonService reportReasonService;

    @Operation(summary = "举报原因列表", description = "用途：管理端列表（按 order 升序，**含已停用**，不分页）；带 feedbackCount 供删除前判断。")
    @GetMapping
    public Result<List<ReportReasonAdminVO>> list() {
        return Result.success(reportReasonService.listAllForAdmin());
    }

    @Operation(summary = "新增举报原因", description = "用途：新增（默认**启用**、排最后）。机器值 1~32、小写字母/数字/-、全站唯一；启用数上限 8。")
    @PostMapping
    public Result<ReportReasonAdminVO> create(@Valid @RequestBody ReportReasonSaveReq req) {
        return Result.success(reportReasonService.create(req.getValue(), req.getLabel()));
    }

    @Operation(summary = "举报原因改名", description = "用途：**只改 label**（改名免费，历史举报的中文翻译实时生效）；不存在 → 4001。")
    @PutMapping("/{id}")
    public Result<Void> rename(
            @Parameter(description = "原因ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody ReportReasonRenameReq req) {
        reportReasonService.rename(id, req.getLabel());
        return Result.success();
    }

    @Operation(summary = "举报原因启停", description = "用途：只改 status。**停用最后一条启用 → 400**（举报入口不能配空）；启用数超 8 → 400；不存在 → 4001。")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @Parameter(description = "原因ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody ReportReasonStatusReq req) {
        reportReasonService.updateStatus(id, req.getStatus());
        return Result.success();
    }

    @Operation(summary = "举报原因排序", description = "用途：拖拽后**整体提交全量行**（`{ items: [{ id, order }] }`）；缺行 / 重复 → 400。")
    @PutMapping("/sort")
    public Result<Void> sort(@Valid @RequestBody SortItemsReq req) {
        reportReasonService.sort(req.getItems());
        return Result.success();
    }

    @Operation(summary = "删除举报原因", description = "用途：删除。**被举报记录引用 → 400**（下线一律用停用，避免历史举报翻不出中文）；不存在 → 4001。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "原因ID", example = "1") @PathVariable Long id) {
        reportReasonService.delete(id);
        return Result.success();
    }
}
