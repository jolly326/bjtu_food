package com.bjtufood.feedback.controller.admin;

import com.bjtufood.common.result.Result;
import com.bjtufood.feedback.dto.OptionAdminVO;
import com.bjtufood.feedback.dto.OptionRenameReq;
import com.bjtufood.feedback.dto.OptionSaveReq;
import com.bjtufood.feedback.dto.OptionStatusReq;
import com.bjtufood.feedback.service.ReportOptionService;
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
 * 举报原因管理（管理端）。
 *
 * <p>契约真源：docs/api/web/report-options.md。
 * <p><b>数据锚在原因 ID</b>（后端生成、历史举报按它落库）—— 没有「改 ID」的端点；改 `name` 免费。
 * <p>公开只读端点见 {@code GET /report-options}（{@code OptionDictionaryController}），
 * 出参结构与反馈类型字典同形（只下发启用项）。
 */
@Tag(name = "12. 后台举报原因管理", description = "管理员维护举报弹层的原因字典：列表（含已停用）/ 新增 / 改名 / 启停 / 删除。"
        + "数据锚在原因 ID；不变量：删除受引用约束、至少保留 1 条启用、启用 ≤8 条。需要管理员 token。")
@RestController
@RequestMapping("/admin/report-options")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class ReportOptionAdminController {

    private final ReportOptionService reportOptionService;

    @Operation(summary = "举报原因列表", description = "用途：管理端列表（按 id 升序，**含已停用**，不分页）；带 usageCount 供删除前判断。")
    @GetMapping
    public Result<List<OptionAdminVO>> list() {
        return Result.success(reportOptionService.listAllForAdmin());
    }

    @Operation(summary = "新增举报原因", description = "用途：新增（默认**启用**）。只填中文名（1~32 字，唯一）；原因 ID 由后端生成；启用数上限 8。")
    @PostMapping
    public Result<OptionAdminVO> create(@Valid @RequestBody OptionSaveReq req) {
        return Result.success(reportOptionService.create(req.getName()));
    }

    @Operation(summary = "举报原因改名", description = "用途：**只改 name**（改名免费，历史举报的中文翻译实时生效）；不存在 → 4001。")
    @PutMapping("/{id}")
    public Result<Void> rename(
            @Parameter(description = "原因ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody OptionRenameReq req) {
        reportOptionService.rename(id, req.getName());
        return Result.success();
    }

    @Operation(summary = "举报原因启停", description = "用途：只改 status。**停用最后一条启用 → 400**（举报入口不能配空）；启用数超 8 → 400；不存在 → 4001。")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @Parameter(description = "原因ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody OptionStatusReq req) {
        reportOptionService.updateStatus(id, req.getStatus());
        return Result.success();
    }

    @Operation(summary = "删除举报原因", description = "用途：删除。**被举报记录引用 → 400**（下线一律用停用，避免历史举报翻不出中文）；不存在 → 4001。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "原因ID", example = "1") @PathVariable Long id) {
        reportOptionService.delete(id);
        return Result.success();
    }
}
