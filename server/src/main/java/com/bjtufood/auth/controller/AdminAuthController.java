package com.bjtufood.auth.controller;

import com.bjtufood.auth.dto.AdminLoginReq;
import com.bjtufood.auth.dto.AdminLoginVO;
import com.bjtufood.auth.dto.AdminMeVO;
import com.bjtufood.auth.entity.AdminAccount;
import com.bjtufood.auth.service.AdminAccountService;
import com.bjtufood.auth.support.AdminJwtUtil;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.exception.UnauthorizedException;
import com.bjtufood.common.ratelimit.IpRateLimiter;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.ClientIpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


/**
 * 管理端鉴权端点（TD-19 / TD-21 · P0-4）。
 *
 * <p><b>替代</b>原「静态口令 + 无登录」形态：口令不再下发到前端，改由服务端验证后签发
 * **短期可吊销**的 JWT。设计见 {@code docs/secur/server/server-01-鉴权与访问控制.md}。
 *
 * <p>🔴 <b>{@code POST /admin/auth/login} 必须在鉴权白名单内</b>（见 {@code SecurityConfig#PUBLIC_ANY_METHOD}）——
 * 否则登录请求自身先被拦，<b>功能完全不可用</b>（表现为「登录总是 401」）。
 */
@Tag(name = "管理端鉴权", description = "管理员账密登录（换 token）。login 为公开端点，其余 /admin/** 需带 Authorization: Bearer <token>。")
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAccountService adminAccountService;
    private final AdminJwtUtil adminJwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final IpRateLimiter ipRateLimiter;

    /**
     * 登录限频（TD-21）：同 IP 每分钟 ≤5 次、每小时 ≤20 次。
     * <p>
     * <b>阈值依据</b>：BCrypt 比对单次约 100ms（故意拖慢爆破）。正常用户输错几次即重试，
     * 5 次/分已足够宽裕；而 20 次/小时把可尝试次数压到远低于常用口令的组合空间。
     */
    private static final IpRateLimiter.Rule LOGIN_PER_MINUTE = new IpRateLimiter.Rule(5, 60_000L);

    private static final IpRateLimiter.Rule LOGIN_PER_HOUR = new IpRateLimiter.Rule(20, 3_600_000L);

    /**
     * 🔴 <b>登录失败统一提示</b>：不区分「账号不存在」与「密码错误」，
     * 也不单独提示「账号已停用」。
     *
     * <p><b>为什么必须统一</b>：若分别提示，攻击者可据此批量探测**哪些账号存在**
     * （用户名枚举），再对少量有效账号集中爆破。
     */
    private static final String LOGIN_FAILED_MESSAGE = "账号或密码错误";

    @PostMapping("/login")
    @Operation(summary = "管理员账密登录",
            description = "公开端点。账号密码正确后签发管理端 JWT（默认 24h 有效）。"
                    + "失败一律返回「账号或密码错误」，不区分原因（防用户名枚举）。同 IP 限频 5/分 · 20/时。")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginReq req) {
        // 限频先于查库：爆破的请求主体在此即被挡下，不给数据库压力
        long waitSeconds = ipRateLimiter.tryAcquire(
                "admin-login", ClientIpUtil.resolveCurrent(), LOGIN_PER_MINUTE, LOGIN_PER_HOUR);
        if (waitSeconds > 0) {
            throw new BusinessException("登录过于频繁，请 " + waitSeconds + " 秒后再试");
        }

        AdminAccount account = adminAccountService.findByUsername(req.getUsername());

        // 🔴 三种失败（账号不存在 / 密码错 / 已停用）**走同一条分支、同一提示**，
        // 且都不回显输入的账号，避免账号枚举。
        // 账号不存在时仍执行一次口令比对，使「响应耗时」不随账号是否存在而变化。
        boolean passwordMatched = account != null
                && passwordEncoder.matches(req.getPassword(), account.getPasswordHash());
        if (account == null || !passwordMatched || !account.isActive()) {
            // 🔴 401（契约真源 api/web/auth.md「错误码」）：登录失败统一 401 + 「账号或密码错误」，
            // 不回显账号、不区分原因（防用户名枚举）。401 对端上是「会话失效」的唯一信号，
            // 已在登录页上 ⇒ 清空已存 session 即可，不产生跳转环。
            throw new UnauthorizedException(LOGIN_FAILED_MESSAGE);
        }

        String token = adminJwtUtil.createToken(account.getId(), account.getUsername());
        adminAccountService.touchLastLogin(account.getId());

        AdminLoginVO vo = new AdminLoginVO();
        vo.setToken(token);
        vo.setUsername(account.getUsername());
        vo.setExpiresIn(adminJwtUtil.getExpirationSeconds());
        return Result.success(vo);
    }

    /**
     * 时间格式 —— 契约真源 {@code docs/api/web/auth.md}：{@code lastLoginAt} 为
     * {@code yyyy-MM-dd HH:mm:ss}。
     */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/me")
    @Operation(summary = "读取当前登录的管理员",
            description = "需带 Authorization: Bearer <token>。用于端上刷新页面时校验 token 是否仍有效。")
    public Result<AdminMeVO> me() {
        // 走到这里说明 AdminAuthFilter 已完成「取 token → 去 Bearer 前缀 → 验签 → 判过期」，
        // 并把账号 ID 作为 principal 放进了 SecurityContext —— 此处不重复验签，按 ID 回查账号即可。
        AdminAccount account = adminAccountService.findById(currentAccountId());
        if (account == null) {
            // 账号已删除而 token 仍在有效期内 ⇒ 一律按「会话失效」处理，端上据此清 token 回登录页
            throw new UnauthorizedException("管理端登录已失效，请重新登录");
        }
        AdminMeVO vo = new AdminMeVO();
        vo.setUsername(account.getUsername());
        LocalDateTime lastLoginAt = account.getLastLoginAt();
        vo.setLastLoginAt(lastLoginAt == null ? null : TIME_FORMATTER.format(lastLoginAt));
        return Result.success(vo);
    }

    /**
     * 取 {@code AdminAuthFilter} 放进 SecurityContext 的管理员账号 ID。
     *
     * @throws UnauthorizedException 取不到（过滤器未执行或上下文已被清理）—— 按会话失效处理
     */
    private Long currentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long accountId)) {
            throw new UnauthorizedException("管理端登录已失效，请重新登录");
        }
        return accountId;
    }
}