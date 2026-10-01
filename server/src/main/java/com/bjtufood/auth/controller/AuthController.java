package com.bjtufood.auth.controller;

import com.bjtufood.auth.dto.EmailCodeReq;
import com.bjtufood.auth.dto.LoginVO;
import com.bjtufood.auth.dto.ProfileUpdateReq;
import com.bjtufood.auth.dto.UserInfoVO;
import com.bjtufood.auth.dto.VerifyEmailReq;
import com.bjtufood.auth.dto.WechatLoginReq;
import com.bjtufood.auth.service.AuthService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.ratelimit.IpRateLimiter;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.ClientIpUtil;
import com.bjtufood.auth.support.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "01. 认证与用户", description = "微信静默登录、学号邮箱认证、个人资料、用户统计。登录成功后将 data.token 填入 Swagger UI Authorize。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final IpRateLimiter ipRateLimiter;

    /** IP 限频（P3/BE-105）：同 IP 每分钟 ≤3 次，防瞬时并发耗尽 SMTP 配额 */
    private static final IpRateLimiter.Rule EMAIL_CODE_PER_MINUTE = new IpRateLimiter.Rule(3, 60_000L);
    /** IP 限频（P3/BE-105）：同 IP 每小时 ≤10 次，补齐「换邮箱绕过 60s 冷却」的缺口 */
    private static final IpRateLimiter.Rule EMAIL_CODE_PER_HOUR = new IpRateLimiter.Rule(10, 3_600_000L);

    /**
     * IP 限频（A1 防爆破）：{@code /auth/verify-email} 同 IP 每分钟 ≤5 次。
     * <p>
     * 该接口的凭据是 6 位数字（码空间 10⁶），且发码接口匿名 ⇒ 服务端无法按归属收窄匹配范围，
     * 只能让「枚举」不成立：本闸门口径取「正常用户一分钟内不会输错 5 次」，而 BCrypt 比对单次约 100ms，
     * 5 次/分钟 + 20 次/小时（见下）把可尝试次数压到 10⁶ 之上 5 个数量级。
     * 每用户维度另有 {@code VerifyCodeAttemptGuard}（10 次/15 分钟封禁）兜住换 IP 绕过。
     */
    private static final IpRateLimiter.Rule VERIFY_EMAIL_PER_MINUTE = new IpRateLimiter.Rule(5, 60_000L);
    /** IP 限频（A1）：同 IP 每小时 ≤20 次，防「换 IP 轮询」绕过分钟闸 */
    private static final IpRateLimiter.Rule VERIFY_EMAIL_PER_HOUR = new IpRateLimiter.Rule(20, 3_600_000L);

    /**
     * IP 限频（A2）：{@code /auth/wechat-login} 同 IP 每分钟 ≤30 次。
     * <p>
     * 该接口原先**无任何频控**，而每次调用都会外呼一次微信 {@code code2Session}，且新 openid 会建号
     * ⇒ 可被脚本刷（打爆微信侧配额 / 灌 user 表）。阈值对齐既有先例
     * {@code DishController#checkViewIpRateLimit}（30/分 + 300/时）：正常「冷启动才调一次」的用量
     * 远低于此，同时容忍校园网 NAT 共享出口 IP 的同 IP 多用户突发。
     */
    private static final IpRateLimiter.Rule WECHAT_LOGIN_PER_MINUTE = new IpRateLimiter.Rule(30, 60_000L);
    /** IP 限频（A2）：同 IP 每小时 ≤300 次 */
    private static final IpRateLimiter.Rule WECHAT_LOGIN_PER_HOUR = new IpRateLimiter.Rule(300, 3_600_000L);

    /** 请求头中 Token 的前缀（与 JwtAuthFilter 保持一致） */
    private static final String TOKEN_PREFIX = "Bearer ";

    @Operation(
            summary = "获取邮箱验证码（认证用途）",
            description = """
                    用途：向 @bjtu.edu.cn 校园邮箱发送 6 位验证码，用于学号邮箱认证。
                    校园邮箱 = {学号}@bjtu.edu.cn，传 username（学号）即可自动推导邮箱，无需填 email。
                    规则：同一邮箱 60 秒内不能重复发送，验证码 10 分钟有效。验证码经邮件发送，不会在响应中返回。
                    同 IP 每分钟 ≤3 次、每小时 ≤10 次。
                    出参：data 为 null（无载荷，成功即 code=200）。
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "username": "20240001"
                    }
                    """)))
    )
    @PostMapping("/auth/email-code")
    public Result<Void> createEmailCode(@Valid @RequestBody EmailCodeReq req) {
        checkEmailCodeIpRateLimit();
        authService.createEmailCode(req.getUsername());
        // 无载荷：成功文案由统一响应壳 message 承担（原 data={message} 是第二条消息通道，端上零消费）
        return Result.success();
    }

    /**
     * IP 维度限频（P3/BE-105）：/auth/email-code 原仅邮箱维度 60s 冷却
     * （EmailCodeServiceImpl.checkRateLimit），换邮箱即可绕过刷码。
     * 接入层防护放 Controller（非业务逻辑）；邮箱维度冷却仍归 Service。
     */
    private void checkEmailCodeIpRateLimit() {
        long waitSeconds = ipRateLimiter.tryAcquire(
                "email-code", ClientIpUtil.resolveCurrent(), EMAIL_CODE_PER_MINUTE, EMAIL_CODE_PER_HOUR);
        if (waitSeconds > 0) {
            throw new BusinessException("验证码发送过于频繁，请 " + waitSeconds + " 秒后再试");
        }
    }

    /**
     * IP 维度限频：验证码校验（A1 防 6 位码暴力枚举；另一种防护见 {@code VerifyCodeAttemptGuard}）。
     * <p>
     * 文案含「过于频繁」+「请 N 秒后再试」：与 {@code client/src/api/http.ts} 的限频识别
     * （{@code RATE_LIMIT_PATTERNS} + {@code parseRetryAfter}）对齐，端上可做退避倒计时。
     */
    private void checkVerifyEmailIpRateLimit() {
        long waitSeconds = ipRateLimiter.tryAcquire(
                "verify-email", ClientIpUtil.resolveCurrent(), VERIFY_EMAIL_PER_MINUTE, VERIFY_EMAIL_PER_HOUR);
        if (waitSeconds > 0) {
            throw new BusinessException("操作过于频繁，请 " + waitSeconds + " 秒后再试");
        }
    }

    /**
     * IP 维度限频：微信静默登录（A2：防外呼配额被刷与批量建号）。
     * <p>
     * 文案与上限口径同 {@code checkVerifyEmailIpRateLimit}。
     */
    private void checkWechatLoginIpRateLimit() {
        long waitSeconds = ipRateLimiter.tryAcquire(
                "wechat-login", ClientIpUtil.resolveCurrent(), WECHAT_LOGIN_PER_MINUTE, WECHAT_LOGIN_PER_HOUR);
        if (waitSeconds > 0) {
            throw new BusinessException("操作过于频繁，请 " + waitSeconds + " 秒后再试");
        }
    }

    @Operation(
            summary = "微信静默登录",
            description = """
                    用途：小程序启动时调用 wx.login 获取 code，后端 code2Session 换 openid 自动登录。
                    新 openid 自动建号（游客态 verified=false）；已有 openid 直接返回原账号。返回 { token, userInfo }。
                    风控：同 IP 每分钟 ≤30 次、每小时 ≤300 次（防脚本刷微信外呼配额与批量建号）。
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "code": "0a3b...（wx.login 临时凭证）"
                    }
                    """)))
    )
    @PostMapping("/auth/wechat-login")
    public Result<LoginVO> wechatLogin(@Valid @RequestBody WechatLoginReq req) {
        checkWechatLoginIpRateLimit();
        return Result.success(authService.wechatLogin(req.getCode()));
    }

    @Operation(
            summary = "学号邮箱认证（绑定/合并/替换）",
            description = """
                    用途：游客完成学号邮箱认证（verified=true）。入参仅验证码，绑定邮箱由验证码记录推导，当前微信账号从登录态取。
                    认证通过后：无历史邮箱则直接绑定；存在历史邮箱账号则数据归属转移（旧账号业务数据改挂到当前微信）；
                    邮箱已被他微信绑定则替换绑定（旧微信 verified=false、bind_email=NULL）。返回更新后 UserInfoVO（bind_email 已写入；JWT 不含 bind_email、实时查库，无需重发 token）。
                    风控：同 IP 每分钟 ≤5 次、每小时 ≤20 次；同一账号 15 分钟内失败满 10 次将被暂时拒绝校验
                    （发码接口匿名 ⇒ 服务端无「谁申请了这条码」的归属信息，故以「让枚举不成立」防 6 位码被暴力猜中）。
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "code": "123456"
                    }
                    """)))
    )
    @PostMapping("/auth/verify-email")
    public Result<UserInfoVO> verifyEmail(@Valid @RequestBody VerifyEmailReq req) {
        checkVerifyEmailIpRateLimit();
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(authService.verifyEmail(req.getCode(), userId));
    }

    @Operation(
            summary = "获取当前用户资料",
            description = "用途：个人中心进入时读取当前登录用户的昵称、头像、认证状态（由 bindEmail 非空派生，不作独立出参字段）、绑定邮箱（bindEmail）。字段集与登录 / 认证链路一致（恰 4 项：id / nickname / avatar / bindEmail）。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/auth/profile")
    public Result<UserInfoVO> profile() {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(authService.getProfile(userId));
    }

    @Operation(
            summary = "修改当前用户资料",
            description = "用途：修改昵称或头像。头像地址须先取得：小程序端走云存储链路 POST /upload/cloud-image（返回 URL）后作为 avatar 保存。",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "nickname": "新的昵称",
                      "avatar": "/images/seed/dishes/tomato-egg.jpg"
                    }
                    """)))
    )
    @PutMapping("/auth/profile")
    public Result<UserInfoVO> updateProfile(@Valid @RequestBody ProfileUpdateReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(authService.updateProfile(userId, req));
    }

    @Operation(
            summary = "注销账号（匿名化）",
            description = """
                    用途：用户主动注销当前登录账号（合规硬需求）。语义为匿名化而非物理删除：
                    昵称置为「已注销用户」，头像/邮箱/微信绑定全部清空，认证状态复位，
                    username 改写为 deleted_{id} 以释放唯一键（同一微信可重新静默登录建新游客号）；
                    历史评价与反馈保留（展示昵称随 join user 自然变为「已注销用户」，评分聚合不破坏），
                    浏览足迹与系统通知随注销删除，该用户验证码记录删除。
                    注销后当前 token 立即失效（其余设备的历史 token 在单实例未重启期间一并失效）。
                    **不可恢复**：同一微信重新登录创建全新账号，旧账号数据不归属新账号。
                    终态保护：已注销账号重复调用返回 400「账号已注销」。
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @DeleteMapping("/auth/account")
    public Result<Void> deleteAccount(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        authService.deleteAccount(userId, extractToken(request));
        return Result.success();
    }

    /** 从当前请求头提取 JWT（兼容 Authorization: Bearer 与 Swagger bearerAuth 头），供注销后拉黑 */
    private String extractToken(HttpServletRequest request) {
        String token = extractTokenFromHeader(request.getHeader("Authorization"));
        if (token == null) {
            token = extractTokenFromHeader(request.getHeader("bearerAuth"));
        }
        return token;
    }

    private String extractTokenFromHeader(String header) {
        if (!StringUtils.hasText(header)) {
            return null;
        }
        String token = header.trim();
        while (token.regionMatches(true, 0, TOKEN_PREFIX, 0, TOKEN_PREFIX.length())) {
            token = token.substring(TOKEN_PREFIX.length()).trim();
        }
        return token;
    }
}
