package com.bjtufood.feedback.controller;

import com.bjtufood.auth.support.SecurityUtil;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.ratelimit.IpRateLimiter;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.ClientIpUtil;
import com.bjtufood.feedback.dto.ReportReq;
import com.bjtufood.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价举报端点（RESTful 子资源：{@code POST /reviews/{id}/report}）。
 * <p>
 * <b>写入口拆分</b>：举报、意见反馈、菜品纠错三条链路各自独立——
 * 反馈 {@code POST /feedback}、纠错 {@code POST /dishes/{id}/correction}、
 * 举报 {@code POST /reviews/{id}/report}（本端点）；三者各为独立 DTO / 校验，
 * 但底层仍共用 {@code user_feedback} 表与处置 / 回执服务。
 * <p>
 * 举报属<b>免认证公开行为</b>（游客可提交）；被举报对象由路径 {@code {id}} 表达（评价 ID）。
 */
@Tag(name = "用户反馈", description = "评价举报（RESTful 子资源路径）")
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReportController {

    private final FeedbackService feedbackService;
    private final IpRateLimiter ipRateLimiter;

    /** IP 限频：同 IP 每分钟 ≤2 条 */
    private static final IpRateLimiter.Rule RULE_PER_MINUTE = new IpRateLimiter.Rule(2, 60_000L);
    /** IP 限频：同 IP 每小时 ≤10 条 */
    private static final IpRateLimiter.Rule RULE_PER_HOUR = new IpRateLimiter.Rule(10, 3_600_000L);

    @Operation(summary = "举报评价",
            description = "PUB。对指定评价提交举报（结构化原因单选为准，文本可空）。"
                    + "写入 user_feedback（type=report，status=pending）。"
                    + "同 IP 每分钟 ≤2 条、每小时 ≤10 条；被举报评价不存在或不可见 → 4001。"
                    + "测试示例：POST /reviews/3/report {\"reason\":\"spam\"}")
    @PostMapping("/{id}/report")
    public Result<Void> report(
            @Parameter(description = "被举报的评价ID", example = "3")
            @PathVariable Long id,
            @Valid @RequestBody ReportReq req) {
        long waitSeconds = ipRateLimiter.tryAcquire(
                "review-report", ClientIpUtil.resolveCurrent(), RULE_PER_MINUTE, RULE_PER_HOUR);
        if (waitSeconds > 0) {
            throw new BusinessException("提交过于频繁，请 " + waitSeconds + " 秒后再试");
        }
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        feedbackService.report(userId, id, req);
        return Result.success();
    }
}
