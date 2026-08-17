package com.bjtufood.moment.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.annotation.AuditLog;
import com.bjtufood.common.constant.OperationLogConst;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.moment.dto.MomentVO;
import com.bjtufood.moment.entity.MomentComment;
import com.bjtufood.moment.entity.MomentCommentUseful;
import com.bjtufood.moment.mapper.MomentCommentMapper;
import com.bjtufood.moment.mapper.MomentCommentUsefulMapper;
import com.bjtufood.moment.service.MomentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 动态管理接口（Web 后台，ADM，W5）
 */
@Tag(name = "16. 后台动态管理", description = "动态列表 / 强制下架 / 删除动态 / 评论治理。需要管理员 token。")
@RestController
@RequestMapping("/admin/moments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MomentAdminController {

    private final MomentService momentService;
    private final MomentCommentMapper momentCommentMapper;
    private final MomentCommentUsefulMapper momentCommentUsefulMapper;

    // ===== 动态评论治理（单条评论查看 / 删除，不引入隐藏字段） =====

    @Operation(summary = "评论列表", description = "ADM。按动态或用户过滤全部评论（含子回复），供管理台审阅。")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/comments")
    public Result<List<MomentComment>> listComments(
            @Parameter(description = "动态ID（可选）")
            @RequestParam(required = false) Long momentId,
            @Parameter(description = "评论用户ID（可选，用户行为聚合用）")
            @RequestParam(required = false) Long userId) {
        LambdaQueryWrapper<MomentComment> w = new LambdaQueryWrapper<MomentComment>()
                .orderByDesc(MomentComment::getCreatedAt)
                .last("LIMIT 200");
        if (momentId != null) w.eq(MomentComment::getMomentId, momentId);
        if (userId != null) w.eq(MomentComment::getUserId, userId);
        return Result.success(momentCommentMapper.selectList(w));
    }

    @Operation(summary = "删除评论", description = "ADM。物理删除评论，级联删除其子回复与该评论的「有用」标记。")
    @AuditLog(action = OperationLogConst.ACTION_MOMENT_COMMENT_DELETE, targetType = "moment_comment", targetId = "#id")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(
            @Parameter(description = "评论ID", example = "1")
            @PathVariable Long id) {
        if (momentCommentMapper.selectById(id) == null) {
            throw new BusinessException("评论不存在");
        }
        // 级联：子回复（parent_id=id）+ 本评论的 useful 标记
        momentCommentMapper.delete(new LambdaQueryWrapper<MomentComment>()
                .eq(MomentComment::getParentId, id));
        momentCommentUsefulMapper.delete(new LambdaQueryWrapper<MomentCommentUseful>()
                .eq(MomentCommentUseful::getCommentId, id));
        momentCommentMapper.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "动态管理列表", description = "ADM。返回含全部状态的动态（approved+status=0、已下架 status=1、pending 等），供管理台分段展示；支持 status/auditStatus/userId 过滤。")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping
    public Result<PageResult<MomentVO>> list(
            @Parameter(description = "下架状态：0=正常 1=下架（可选）")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "审核状态：pending/approved/rejected（可选）")
            @RequestParam(required = false) String auditStatus,
            @Parameter(description = "发布用户ID（可选，用户行为聚合用）")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "内容关键词（可选，对动态正文模糊匹配）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") int pageSize) {
        IPage<MomentVO> result = momentService.adminList(status, auditStatus, userId, keyword, page, pageSize);
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
