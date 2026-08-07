package com.bjtufood.moment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.moment.dto.MomentCommentReq;
import com.bjtufood.moment.dto.MomentCommentVO;
import com.bjtufood.moment.dto.MomentPublishReq;
import com.bjtufood.moment.dto.MomentUsefulResult;
import com.bjtufood.moment.dto.MomentVO;
import com.bjtufood.moment.service.MomentService;
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
 * 社区动态接口（task-06）
 * <p>
 * 列表/详情/评论浏览为公开（GET /moments/** 在 SecurityConfig 中被 method=GET 放行）；
 * 写操作需登录（STU）。
 */
@Tag(name = "06. 社区动态", description = "社区广场、动态详情、发布/编辑/删除、有用、评论。发布需登录。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class MomentController {

    private final MomentService momentService;

    @Operation(summary = "社区广场列表", description = "PUB。仅返回 approved 且 status=0。支持 dishId/stallId 关联过滤，canteenId 按食堂下全部档口聚合。tab 预留（recommend 暂等价 latest）。")
    @GetMapping("/moments")
    public Result<PageResult<MomentVO>> list(
            @Parameter(description = "排序：recommend/latest（默认 latest）", example = "latest")
            @RequestParam(defaultValue = "latest") String tab,
            @Parameter(description = "关联菜品ID过滤", example = "1")
            @RequestParam(required = false) Long dishId,
            @Parameter(description = "关联档口ID过滤", example = "1")
            @RequestParam(required = false) Long stallId,
            @Parameter(description = "关联食堂ID过滤（按该食堂下全部档口聚合关联动态，task-12.6）", example = "1")
            @RequestParam(required = false) Long canteenId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        IPage<MomentVO> result = momentService.publicList(tab, dishId, stallId, canteenId, page, pageSize);
        return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
    }

    @Operation(summary = "动态详情", description = "PUB。作者本人可见 rejectReason。")
    @GetMapping("/moments/{id}")
    public Result<MomentVO> detail(
            @Parameter(description = "动态ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        return Result.success(momentService.detail(id, userId));
    }

    @Operation(summary = "发布动态", description = "STU。audit_status=pending。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/moments")
    public Result<Map<String, Long>> publish(@Valid @RequestBody MomentPublishReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        Long id = momentService.publish(userId, req);
        return Result.success(Map.of("id", id));
    }

    @Operation(summary = "编辑重提动态", description = "STU 仅作者。复用原记录，audit_status→pending，reject_reason 清空。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @PutMapping("/my/moments/{id}")
    public Result<Void> updateMine(
            @Parameter(description = "动态ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody MomentPublishReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        momentService.updateMoment(id, userId, req);
        return Result.success();
    }

    @Operation(summary = "删除自己动态", description = "STU 仅作者。物理删除，连带评论/通知。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @DeleteMapping("/my/moments/{id}")
    public Result<Void> deleteMine(
            @Parameter(description = "动态ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        momentService.deleteMoment(id, userId);
        return Result.success();
    }

    @Operation(summary = "我的动态列表", description = "STU 补齐契约缺口。按当前用户查询，支持 auditStatus 过滤（pending/approved/rejected），返回含 audit_status。")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my/moments")
    public Result<List<MomentVO>> myMoments(
            @Parameter(description = "审核状态过滤：pending/approved/rejected（可空=全部）")
            @RequestParam(required = false) String auditStatus) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(momentService.myMoments(userId, auditStatus));
    }

    @Operation(summary = "👍 有用切换（幂等）", description = "STU。一人一票，未标记→true+1，已标记→false-1。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/moments/{id}/useful")
    public Result<MomentUsefulResult> toggleUseful(
            @Parameter(description = "动态ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(momentService.toggleUseful(id, userId));
    }

    @Operation(summary = "发评论", description = "STU。支持 parentId 一层回复。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/moments/{id}/comments")
    public Result<Map<String, Long>> comment(
            @Parameter(description = "动态ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody MomentCommentReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        Long cid = momentService.comment(id, userId, req);
        return Result.success(Map.of("id", cid));
    }

    @Operation(summary = "评论列表", description = "PUB。按 created_at asc，扁平化带 parentId/replyToNickname；登录用户带 👍 标记。")
    @GetMapping("/moments/{id}/comments")
    public Result<PageResult<MomentCommentVO>> commentList(
            @Parameter(description = "动态ID", example = "1")
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        IPage<MomentCommentVO> result = momentService.commentList(id, userId, page, pageSize);
        return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
    }

    @Operation(summary = "评论 👍 有用切换（幂等）", description = "STU。一人一票，未标记→true+1，已标记→false-1。返回 {useful,usefulCount}。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/moments/{id}/comments/{cid}/useful")
    public Result<MomentUsefulResult> toggleCommentUseful(
            @Parameter(description = "动态ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "评论ID", example = "1")
            @PathVariable Long cid) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(momentService.toggleCommentUseful(id, cid, userId));
    }

    @Operation(summary = "删除自己评论", description = "STU 仅作者。连带子回复删除，commentCount-1。", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('STUDENT')")
    @DeleteMapping("/my/moments/{id}/comments/{cid}")
    public Result<Void> deleteComment(
            @Parameter(description = "动态ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "评论ID", example = "1")
            @PathVariable Long cid) {
        Long userId = SecurityUtil.getCurrentUserId();
        momentService.deleteComment(id, cid, userId);
        return Result.success();
    }
}
