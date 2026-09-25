package com.bjtufood.auth.controller;

import com.bjtufood.auth.dto.EmailCodeReq;
import com.bjtufood.auth.dto.LoginVO;
import com.bjtufood.auth.dto.ProfileUpdateReq;
import com.bjtufood.auth.dto.UserInfoVO;
import com.bjtufood.auth.dto.VerifyEmailReq;
import com.bjtufood.auth.dto.WechatLoginReq;
import com.bjtufood.auth.service.AuthService;
import com.bjtufood.common.config.IpRateLimiter;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.ClientIpUtil;
import com.bjtufood.common.utils.SecurityUtil;
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

import java.util.Map;

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

    /** 请求头中 Token 的前缀（与 JwtAuthFilter 保持一致） */
    private static final String TOKEN_PREFIX = "Bearer ";

    @Operation(
            summary = "获取邮箱验证码（认证用途）",
            description = """
                    用途：向 @bjtu.edu.cn 校园邮箱发送 6 位验证码，用于学号邮箱认证。
                    校园邮箱 = {学号}@bjtu.edu.cn，传 username（学号）即可自动推导邮箱，无需填 email。
                    规则：同一邮箱 60 秒内不能重复发送，验证码 10 分钟有效。验证码经邮件发送，不会在响应中返回。
                    同 IP 每分钟 ≤3 次、每小时 ≤10 次。
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "username": "20240001"
                    }
                    """)))
    )
    @PostMapping("/auth/email-code")
    public Result<Map<String, String>> createEmailCode(@Valid @RequestBody EmailCodeReq req) {
        checkEmailCodeIpRateLimit();
        authService.createEmailCode(req.getUsername());
        return Result.success(Map.of("message", "验证码已发送"));
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

    @Operation(
            summary = "微信静默登录",
            description = """
                    用途：小程序启动时调用 wx.login 获取 code，后端 code2Session 换 openid 自动登录。
                    新 openid 自动建号（游客态 verified=false）；已有 openid 直接返回原账号。返回 { token, userInfo }。
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "code": "0a3b...（wx.login 临时凭证）"
                    }
                    """)))
    )
    @PostMapping("/auth/wechat-login")
    public Result<LoginVO> wechatLogin(@Valid @RequestBody WechatLoginReq req) {
        return Result.success(authService.wechatLogin(req.getCode()));
    }

    @Operation(
            summary = "学号邮箱认证（绑定/合并/替换）",
            description = """
                    用途：游客完成学号邮箱认证（verified=true）。入参仅验证码，绑定邮箱由验证码记录推导，当前微信账号从登录态取。
                    认证通过后：无历史邮箱则直接绑定；存在历史邮箱账号则数据归属转移（旧账号业务数据改挂到当前微信）；
                    邮箱已被他微信绑定则替换绑定（旧微信 verified=false、bind_email=NULL）。返回更新后 UserInfoVO（bind_email 已写入；JWT 不含 bind_email、实时查库，无需重发 token）。
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
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(authService.verifyEmail(req.getCode(), userId));
    }

    @Operation(
            summary = "获取当前用户资料",
            description = "用途：个人中心进入时读取当前登录用户的昵称、头像、认证状态（由 bindEmail 非空派生，不作独立出参字段）、绑定邮箱（bindEmail）、注册时间（createdAt）。字段集与登录 / 认证链路一致（恰 6 项：id / username / nickname / avatar / bindEmail / createdAt，2026-09-22 spec §7.32 修订）。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/auth/profile")
    public Result<UserInfoVO> profile() {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(authService.getProfile(userId));
    }

    @Operation(
            summary = "修改当前用户资料",
            description = "用途：修改昵称或头像。头像地址须先取得：小程序端走云存储链路 POST /upload/images（返回 URL）后作为 avatar 保存。",
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
