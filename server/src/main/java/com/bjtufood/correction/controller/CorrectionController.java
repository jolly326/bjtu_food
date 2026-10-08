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
 * 菜品问题反馈接口（用户端，公开写入口）。
 * <p>
 * 请求体以 {@code type} 判别两类反馈（字段 / 图片），两类走完全不同的表单与校验
 * （见 {@code CorrectionServiceImpl#submit}）。
 */
@Tag(name = "菜品问题反馈", description = "用户在菜品详情页提交的问题反馈（信息有误 / 已经下架；独立资源，管理员处置后更新菜品）")
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
     * 提交菜品问题反馈（PUB：游客与登录用户均可，匿名允许——对齐 feedback 提交口径）。
     *
     * @param req <b>type 必填</b>：
     *            <b>field（信息有误）</b> —— 局部提交（patch），只传改动项
     *            （name / price(分) / canteenName / stallName / floor / attributes / images，均为选填），
     *            无任何改动项 → 400「未提交任何改动」，images ≤5 张；
     *            <b>gone（已经下架）</b> —— <b>一键提交即可成立</b>：note（≤200 字）与 images（≤3 张）
     *            <b>均为选填、允许全不传</b>；但传入任何差异项字段 → 400（语义冲突）。
     *            同一用户对同一菜品的 gone 型<b>只计一次</b>（重复提交返回成功、不重复计数）。
     */
    @Operation(summary = "提交菜品问题反馈", description = "PUB。游客与登录用户均可提交（dishId 在路径上）。"
            + "**type 必填**：field=信息有误（局部提交，只传改动项；空改动 → 400；images ≤5 张）"
            + "｜ gone=已经下架（**一键提交即可成立**，note ≤200 字 / images ≤3 张 **均为选填**；"
            + "传差异项字段 → 400；同用户对同一菜品只计一次）。"
            + "floor 传入时非空 ≤16 字（空白 → 400「楼层不能为空」，超长 → 400「楼层超长」），采纳时写回目标档口 stall.floor。"
            + "菜品不存在或已下架返回 4001。写入 dish_correction（type + status=pending）。"
            + "同 IP 每分钟 ≤2 条、每小时 ≤10 条（两类共用额度）。")
    @PostMapping("/dishes/{id}/correction")
    public Result<Void> submitCorrection(
            @Parameter(description = "目标菜品ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "反馈体 {**type**(必填: field|gone), name?, price?, canteenName?, stallName?, "
                    + "floor?, attributes?, images?, note?}；field 型只传改动项，gone 型仅用 note/images（选填）")
            @Valid @RequestBody DishCorrectionReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        checkRateLimit(userId);
        correctionService.submit(userId, id, req);
        return Result.success();
    }

    /**
     * 双维度滥用防护（**IP × 账号**，任一超限即拒）：写请求已绑定账号，
     * 账号维度使「换 IP 刷纠错」同样被拦。接入层频控放 Controller（非业务逻辑），
     * 参数校验与业务仍归 {@code CorrectionService}。
     */
    private void checkRateLimit(Long userId) {
        long waitSeconds = ipRateLimiter.tryAcquire(
                "dish-correction", ClientIpUtil.resolveCurrent(), RULE_PER_MINUTE, RULE_PER_HOUR);
        if (waitSeconds == 0L) {
            waitSeconds = ipRateLimiter.tryAcquire(
                    "dish-correction:user", String.valueOf(userId), RULE_PER_MINUTE, RULE_PER_HOUR);
        }
        if (waitSeconds > 0) {
            throw new BusinessException("提交过于频繁，请 " + waitSeconds + " 秒后再试");
        }
    }
}
