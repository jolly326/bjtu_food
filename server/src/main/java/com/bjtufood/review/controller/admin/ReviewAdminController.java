package com.bjtufood.review.controller.admin;

import com.bjtufood.common.annotation.AuditLog;
import com.bjtufood.common.constant.OperationLogConst;
import com.bjtufood.common.result.Result;
import com.bjtufood.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "10. 后台评价审核", description = "系统管理员查看、隐藏、删除评价。需要管理员 token。")
@RestController
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ReviewAdminController {

    private final ReviewService reviewService;

    @Operation(summary = "全部评价列表", description = "用途：后台查看所有评价，支持按 isHidden/secState/userId 筛选。secState=review 捞内容安全待人工复核队列。测试示例：/admin/reviews?page=1&pageSize=10&isHidden=0&secState=review")
    @GetMapping
    public Result<?> listAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer isHidden,
            @Parameter(description = "内容安全状态筛选：pass/review/rejected（可选）", example = "review")
            @RequestParam(required = false) String secState,
            @Parameter(description = "提交用户ID（可选，用户行为聚合用）")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "评价正文关键词（可选，模糊匹配）")
            @RequestParam(required = false) String keyword) {
        return Result.success(reviewService.listAllForAdmin(page, pageSize, isHidden, secState, userId, keyword));
    }

    @Operation(
            summary = "设置评价内容安全复核结果",
            description = """
                    用途：管理端人工复核机检存疑（secState=review）的评价。
                    body 传 {"state":"pass"} 复核通过（恢复对外可见）或 {"state":"rejected"} 复核不通过（对外不可见）。
                    与「隐藏」接口（/hide）解耦：is_hidden 与 sec_state 互不覆盖。
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(value = """
                            {
                              "state": "pass"
                            }
                            """)))
    )
    @AuditLog(action = OperationLogConst.ACTION_REVIEW_SEC_STATE, targetType = "review", targetId = "#id")
    @PutMapping("/{id}/sec-state")
    public Result<Void> setSecState(
            @Parameter(description = "评价ID", example = "1")
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        String state = body == null ? null : body.get("state");
        reviewService.setSecState(id, state);
        return Result.success();
    }

    @Operation(summary = "设置评价隐藏/显示", description = "用途：显式设置评价隐藏状态（hidden=true 隐藏，false 恢复显示），避免 toggle 语义不确定。隐藏后公开评价列表不再展示。")
    @AuditLog(action = OperationLogConst.ACTION_REVIEW_HIDE, targetType = "review", targetId = "#id")
    @PutMapping("/{id}/hide")
    public Result<Void> setHidden(
            @Parameter(description = "评价ID", example = "1")
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        boolean hidden = body != null && Boolean.TRUE.equals(body.get("hidden"));
        reviewService.setHidden(id, hidden);
        return Result.success();
    }

    @Operation(summary = "管理员删除评价", description = "用途：管理员删除评价。当前实现为物理删除，并触发菜品评分重算。")
    @AuditLog(action = OperationLogConst.ACTION_REVIEW_DELETE, targetType = "review", targetId = "#id")
    @DeleteMapping("/{id}")
    public Result<Void> deleteReview(
            @Parameter(description = "评价ID", example = "1")
            @PathVariable Long id) {
        reviewService.deleteByAdmin(id);
        return Result.success();
    }
}
