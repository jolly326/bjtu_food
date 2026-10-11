package com.bjtufood.feedback.controller;

import com.bjtufood.auth.support.SecurityUtil;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.ratelimit.IpRateLimiter;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.ClientIpUtil;
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
 * 意见反馈提交端点（学生端）。
 * <p>
 * 分类字典（反馈类型 / 举报原因）走独立的公开只读端点，见
 * {@link OptionDictionaryController}；管理端处置见
 * {@code feedback.controller.admin.FeedbackAdminController}。
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
     * 提交反馈（🔑 需登录）：写请求绑定账号 —— 可归因、可按账号维度限频。
     */
    @Operation(summary = "提交反馈",
            description = "🔑 需登录。写入 feedback（optionId=反馈类型字典项；处置状态与处置文案由管理端外置）。"
                    + "optionId 必填且须命中 GET /feedback-options 的启用项；content 必填（≤1000 字）。"
                    + "同 IP 每分钟 ≤2 条、每小时 ≤10 条。")
    @PostMapping("/feedback")
    public Result<Void> submitFeedback(@Valid @RequestBody FeedbackReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        checkRateLimit(userId);
        feedbackService.submit(userId, req);
        return Result.success();
    }

    /**
     * 双维度滥用防护（**IP × 账号**，任一超限即拒）。
     * <p>
     * 🔴 账号维度是**换 IP 也绕不过**的那一道：写请求已绑定账号（🔑 需登录），同一账号在多个
     * IP 上灌库同样会被拦；纯 IP 维度在代理池 / 多出口面前形同虚设。
     * <p>
     * 接入层防护放 Controller（非业务逻辑），参数校验与业务仍归 FeedbackService。
     */
    private void checkRateLimit(Long userId) {
        long waitSeconds = ipRateLimiter.tryAcquire(
                "feedback", ClientIpUtil.resolveCurrent(), RULE_PER_MINUTE, RULE_PER_HOUR);
        if (waitSeconds == 0L) {
            waitSeconds = ipRateLimiter.tryAcquire(
                    "feedback:user", String.valueOf(userId), RULE_PER_MINUTE, RULE_PER_HOUR);
        }
        if (waitSeconds > 0) {
            throw BusinessException.tooManyRequests("提交过于频繁，请 " + waitSeconds + " 秒后再试");
        }
    }
}
