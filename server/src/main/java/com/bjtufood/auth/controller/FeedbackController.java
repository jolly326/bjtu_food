package com.bjtufood.auth.controller;

import com.bjtufood.common.config.IpRateLimiter;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.ClientIpUtil;
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
    private final IpRateLimiter ipRateLimiter;

    /** IP 限频（P3/BE-105）：同 IP 每分钟 ≤2 条，阻断脚本连发 */
    private static final IpRateLimiter.Rule RULE_PER_MINUTE = new IpRateLimiter.Rule(2, 60_000L);
    /** IP 限频（P3/BE-105）：同 IP 每小时 ≤10 条，正常用户会话内提交远低于此 */
    private static final IpRateLimiter.Rule RULE_PER_HOUR = new IpRateLimiter.Rule(10, 3_600_000L);

    /**
     * 提交反馈（PUB：游客与登录用户均可使用，产品决策「反馈不登录也能用」）。
     * 登录用户带 userId；游客 userId 为 null（管理员端可见，昵称显示为空）。
     */
    @Operation(summary = "提交反馈", description = "PUB。游客与登录用户均可提交；写入 user_feedback，status=pending。同 IP 每分钟 ≤2 条、每小时 ≤10 条。")
    @PostMapping("/feedback")
    public Result<Void> submitFeedback(@Valid @RequestBody FeedbackReq req) {
        checkIpRateLimit();
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        feedbackService.submit(userId, req);
        return Result.success();
    }

    /**
     * IP 维度滥用防护（P3/BE-105）：POST /feedback 为 permitAll 公开写入口，
     * 原先无频控可被脚本无限灌库。接入层防护放 Controller（非业务逻辑），
     * 参数校验与业务仍归 FeedbackService。
     */
    private void checkIpRateLimit() {
        long waitSeconds = ipRateLimiter.tryAcquire(
                "feedback", ClientIpUtil.resolveCurrent(), RULE_PER_MINUTE, RULE_PER_HOUR);
        if (waitSeconds > 0) {
            throw new BusinessException("提交过于频繁，请 " + waitSeconds + " 秒后再试");
        }
    }
}
