package com.bjtufood.feedback.controller;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.ratelimit.IpRateLimiter;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.ClientIpUtil;
import com.bjtufood.auth.support.SecurityUtil;
import com.bjtufood.feedback.dto.FeedbackReq;
import com.bjtufood.feedback.dto.ReportReasonVO;
import com.bjtufood.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户反馈接口。
 * <p>
 * 学生端两个端点：提交反馈 {@code POST /feedback} 与举报原因字典 {@code GET /report-reasons}；
 * 管理端 {@code /admin/feedbacks} 由 {@code feedback.controller.admin.FeedbackAdminController} 承载。
 * <p>
 * 举报原因字典独立于 {@code /feedback}：字典是「举报原因」的枚举，<b>不是</b>「反馈提交」的子资源，
 * 其唯一消费者是同仓小程序。
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
     * 举报原因字典（PUB：举报免认证，弹层打开时端上实时拉取）——{@code GET /report-reasons}。
     * 值域与文案唯一真源 = `report_reason` 表（管理端 A7 维护），本端点只下发**启用项**，
     * 端上与管理端零硬编码（PR-12）。
     * <p>
     * <b>公开只读，两端共用</b>（非敏感枚举无需为管理端复制出口）。管理端写操作走
     * {@code /admin/report-reasons}（管理端 JWT 保护）。
     * 构造逻辑共用 {@code FeedbackService#reportReasons()}。
     */
    @Operation(summary = "举报原因字典", description = "PUB。举报时的原因单选项（value 机器值 + label 中文标签），仅含启用项；服务端按 order 升序下发，端上按数组顺序渲染；提交举报时选中的 value 作为 sub 上送。端上零硬编码。测试示例：/report-reasons")
    @GetMapping("/report-reasons")
    public Result<List<ReportReasonVO>> reportReasons() {
        return Result.success(feedbackService.reportReasons());
    }

    /**
     * IP 维度滥用防护：POST /feedback 为 permitAll 公开写入口，无频控可被脚本无限灌库；
     * 接入层防护放 Controller（非业务逻辑），参数校验与业务仍归 FeedbackService。
     */
    private void checkIpRateLimit() {
        long waitSeconds = ipRateLimiter.tryAcquire(
                "feedback", ClientIpUtil.resolveCurrent(), RULE_PER_MINUTE, RULE_PER_HOUR);
        if (waitSeconds > 0) {
            throw new BusinessException("提交过于频繁，请 " + waitSeconds + " 秒后再试");
        }
    }
}
