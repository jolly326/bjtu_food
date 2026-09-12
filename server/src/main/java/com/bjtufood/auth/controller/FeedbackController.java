package com.bjtufood.auth.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.feedback.dto.FeedbackReq;
import com.bjtufood.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户反馈接口（task-09 升级：路径不变，DTO 规范化）
 * 2026-09-07：学生端「我的反馈列表」GET /feedback/my 随前端 getMyFeedback 删除（反馈中心已下线），
 * 提交反馈 POST /feedback 与 admin 端 /admin/feedbacks 保留（Web 后台 FeedbackView 消费）。
 */
@Tag(name = "用户反馈", description = "用户通过联系开发者页面提交反馈")
@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * 提交反馈（PUB：游客与登录用户均可使用，产品决策「反馈不登录也能用」）。
     * 登录用户带 userId；游客 userId 为 null（管理员端可见，昵称显示为空）。
     */
    @Operation(summary = "提交反馈", description = "PUB。游客与登录用户均可提交；写入 user_feedback，status=pending。")
    @PostMapping("/feedback")
    public Result<Void> submitFeedback(@Valid @RequestBody FeedbackReq req) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        feedbackService.submit(userId, req);
        return Result.success();
    }
}
