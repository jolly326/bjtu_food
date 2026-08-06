package com.bjtufood.apply.controller;

import com.bjtufood.apply.dto.ApplyReq;
import com.bjtufood.apply.dto.ApplyVO;
import com.bjtufood.apply.dto.SubmissionVO;
import com.bjtufood.apply.service.ApplyService;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 实体贡献统一申请（学生端，task-12.1）
 */
@Tag(name = "12. 实体贡献统一申请", description = "「我要贡献」统一入口 + 我的申请 + 我的提交聚合。STU。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ApplyController {

    private final ApplyService applyService;

    @Operation(summary = "提交贡献申请", description = "STU。统一收口新增/下架/变更申请。同(entityType,entityId,applyType)已 pending 返 409。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/my/apply")
    public Result<Map<String, Long>> submit(@Valid @RequestBody ApplyReq req) {
        Long applicantId = SecurityUtil.getCurrentUserId();
        Long id = applyService.submit(applicantId, req);
        return Result.success(Map.of("id", id));
    }

    @Operation(summary = "我的申请列表", description = "STU。可按 status 过滤。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my/apply")
    public Result<List<ApplyVO>> myApplies(
            @Parameter(description = "审核状态过滤：pending/approved/rejected")
            @RequestParam(required = false) String status) {
        Long applicantId = SecurityUtil.getCurrentUserId();
        return Result.success(applyService.myApplies(applicantId, status));
    }

    @Operation(summary = "我的提交聚合", description = "STU。聚合本人 apply + moment 两类，前端分「实体/动态」两标签展示。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my/submissions")
    public Result<List<SubmissionVO>> mySubmissions() {
        Long applicantId = SecurityUtil.getCurrentUserId();
        return Result.success(applyService.mySubmissions(applicantId));
    }
}
