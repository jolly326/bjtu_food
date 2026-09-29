package com.bjtufood.correction.controller;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.ratelimit.IpRateLimiter;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.ClientIpUtil;
import com.bjtufood.auth.support.SecurityUtil;
import com.bjtufood.correction.dto.DishCorrectionReq;
import com.bjtufood.correction.service.CorrectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 菜品信息纠错接口（用户端，公开写入口）。
 */
@Tag(name = "菜品信息纠错", description = "用户在菜品详情页提交的信息纠错快照（独立资源，采纳后整体写回菜品）")
@RestController
@RequiredArgsConstructor
public class CorrectionController {

    private final CorrectionService correctionService;
    private final IpRateLimiter ipRateLimiter;

    /** IP 限频（对齐 POST /feedback 口径）：同 IP 每分钟 ≤2 条，阻断脚本连发 */
    private static final IpRateLimiter.Rule RULE_PER_MINUTE = new IpRateLimiter.Rule(2, 60_000L);
    /** IP 限频：同 IP 每小时 ≤10 条，正常用户会话内提交远低于此 */
    private static final IpRateLimiter.Rule RULE_PER_HOUR = new IpRateLimiter.Rule(10, 3_600_000L);

    /**
     * 提交菜品信息纠错（PUB：游客与登录用户均可，匿名允许——对齐 feedback 提交口径）。
     * 局部提交（patch）：只落库用户改动的字段，未改动列留 NULL；采纳与拒绝走管理端 /admin/corrections。
     */
    @Operation(summary = "提交菜品信息纠错", description = "PUB。游客与登录用户均可提交（dishId 在路径上）；"
            + "局部提交——只传改动项（name / price(分) / canteenName / stallName / attributes / images，均为选填）；"
            + "空请求体返回 400「未提交任何改动」。菜品不存在或已下架返回 4001。写入 dish_correction，status=pending。"
            + "同 IP 每分钟 ≤2 条、每小时 ≤10 条。")
    @PostMapping("/dishes/{id}/correction")
    public Result<Void> submitCorrection(
            @Parameter(description = "目标菜品ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "改动项 {name,price(分),canteenName,stallName,attributes,images}；均为选填，传入即校验")
            @Valid @RequestBody DishCorrectionReq req) {
        checkIpRateLimit();
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        correctionService.submit(userId, id, req);
        return Result.success();
    }

    /**
     * IP 维度滥用防护（对齐 {@code FeedbackController#checkIpRateLimit}）：
     * POST /dishes/{id}/correction 为 permitAll 公开写入口，接入层频控放 Controller（非业务逻辑），
     * 参数校验与业务仍归 {@code CorrectionService}。
     */
    private void checkIpRateLimit() {
        long waitSeconds = ipRateLimiter.tryAcquire(
                "dish-correction", ClientIpUtil.resolveCurrent(), RULE_PER_MINUTE, RULE_PER_HOUR);
        if (waitSeconds > 0) {
            throw new BusinessException("提交过于频繁，请 " + waitSeconds + " 秒后再试");
        }
    }
}
