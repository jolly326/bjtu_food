package com.bjtufood.auth.controller;

import com.bjtufood.auth.dto.AdminLoginReq;
import com.bjtufood.auth.dto.AdminLoginVO;
import com.bjtufood.auth.dto.AdminMeVO;
import com.bjtufood.auth.dto.PasswordChangeReq;
import com.bjtufood.auth.entity.AdminAccount;
import com.bjtufood.auth.service.AdminAccountService;
import com.bjtufood.auth.support.AdminJwtUtil;
import com.bjtufood.auth.support.LoginAttemptGuard;
import com.bjtufood.auth.support.PasswordPolicy;
import com.bjtufood.common.alert.AlertType;
import com.bjtufood.common.alert.SecurityAlertNotifier;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.exception.UnauthorizedException;
import com.bjtufood.common.ratelimit.IpRateLimiter;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.ClientIpUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
import java.util.List;

/**
 * 管理端鉴权端点（TD-19 / TD-21 · P0-4）。
 *
 * <p>口令不下发到前端，由服务端验证后签发**短期可吊销**的 JWT（设计见
 * {@code docs/secur/web/登录与凭证.md}）。绑定 MFA 的账号须走两步：
 *
 * <p>🔴 <b>{@code POST /admin/auth/login} 必须在鉴权白名单内</b>
 * （见 {@code SecurityConfig#PUBLIC_ANY_METHOD} 与 {@code AdminAuthFilter}）——
 * 否则这两步自身先被拦，<b>功能完全不可用</b>（表现为「登录总是 401」）。
 *
 * <p>{@code /password} 是**自助端点**：作用于当前登录者本人的账号，
 * 故 {@code AdminAuthFilter} 对其跳过「按角色判定」这一步（只读角色同样需要能改自己的口令），但仍要求有效 token。

 */
@Tag(name = "管理端鉴权", description = "管理员账密登录、改密。login 为公开端点，其余 /admin/** 需带 Authorization: Bearer <token>。")
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAccountService adminAccountService;


    private final AdminJwtUtil adminJwtUtil;

    private final PasswordEncoder passwordEncoder;

    private final IpRateLimiter ipRateLimiter;

    /**
     * 账号维度登录防护（失败计数 + 渐进锁定 + 指数退避）。
     * <p>
     * 🔴 <b>与 {@link #ipRateLimiter} 并行、任一超限即拒</b>：IP 维度防「单源狂打」，
     * 账号维度防「换 IP 的分布式爆破」（计数按用户名累加，跨 IP 生效）。
     * 第二因子校验失败同样计入此处，故行内人拿不到「无限次猜动态口令」的额度。
     */
    private final LoginAttemptGuard loginAttemptGuard;

    /** 安全告警通道（登录失败达阈值 / 登录成功 / 口令变更） */
    private final SecurityAlertNotifier securityAlertNotifier;

    /**
     * 登录限频（TD-21）：同 IP 每分钟 ≤5 次、每小时 ≤20 次。
     * <p>
     * <b>阈值依据</b>：BCrypt 比对单次约 100ms（故意拖慢爆破）。正常用户输错几次即重试，
     * 5 次/分已足够宽裕；而 20 次/小时把可尝试次数压到远低于常用口令的组合空间。
     * 第二步（动态口令）共用同一限频桶 —— 两步同属一次登录。
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

    /** 第二因子失败提示（此时账密已被证明，不存在账号枚举风险） */

    /** 口令超期提示阈值（天）：超过即提示改密，<b>不强制踢出</b> */
    private static final long PASSWORD_AGING_DAYS = 180L;

    @PostMapping("/login")
    @Operation(summary = "管理员账密登录（第一步）",
            description = "公开端点。账号密码正确后：未绑定动态口令 ⇒ 直接签发管理端 JWT（默认 24h）；"
                                + "失败一律返回「账号或密码错误」，不区分原因（防用户名枚举）。"
                    + "双维度限频：同 IP 5/分 · 20/时；同账号按失败次数渐进锁定（5次/5分钟 → 10次/1小时 → 20次/24小时）。")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginReq req) {
        String username = req.getUsername();

        // 🔴 账号维度锁定：先于一切（换 IP 也拦得住）。计数按用户名归一化累加，跨 IP 生效。
        long lockedSeconds = loginAttemptGuard.lockedSeconds(username);
        if (lockedSeconds > 0) {
            throw new BusinessException("登录失败次数过多，请 " + lockedSeconds + " 秒后再试");
        }

        // 限频先于查库：爆破的请求主体在此即被挡下，不给数据库压力
        String clientIp = ClientIpUtil.resolveCurrent();
        long waitSeconds = ipRateLimiter.tryAcquire(
                "admin-login", clientIp, LOGIN_PER_MINUTE, LOGIN_PER_HOUR);
        if (waitSeconds > 0) {
            throw new BusinessException("登录过于频繁，请 " + waitSeconds + " 秒后再试");
        }

        // 指数退避：自第 3 次连续失败起人为延迟（封顶 3s），让「少量尝试」也不经济
        sleepQuietly(loginAttemptGuard.backoffMillis(username));

        AdminAccount account = adminAccountService.findByUsername(username);

        // 🔴 三种失败（账号不存在 / 密码错 / 已停用）**走同一条分支、同一提示**，
        // 且都不回显输入的账号，避免账号枚举。
        // 账号不存在时仍执行一次口令比对，使「响应耗时」不随账号是否存在而变化。
        boolean passwordMatched = account != null
                && passwordEncoder.matches(req.getPassword(), account.getPasswordHash());
        if (account == null || !passwordMatched || !account.isActive()) {
            recordLoginFailure(username, clientIp);
            // 🔴 401（契约真源 api/web/auth.md「错误码」）：登录失败统一 401 + 「账号或密码错误」，
            // 不回显账号、不区分原因（防用户名枚举）。401 对端上是「会话失效」的唯一信号，
            // 已在登录页上 ⇒ 清空已存 session 即可，不产生跳转环。
            throw new UnauthorizedException(LOGIN_FAILED_MESSAGE);
        }

        // 失败计数此时**不清零** —— 第二因子没通过就等于这次登录没成功，
        // 否则「知道口令」的一方可以把爆破第二因子的额度刷成无限。

        loginAttemptGuard.clear(username);
        return Result.success(issueToken(account, clientIp));
    }

    @PostMapping("/password")
    @Operation(summary = "修改管理员口令",
            description = "需带 token，并提供当前口令。强度要求：≥12 位且含大写 / 小写 / 数字 / 符号中至少三类。"
                    + "🔴 改密即刻自增凭证版本 ⇒ **既有 token 全部失效**；响应回一个新 token 供本端续用，"
                    + "其余端需重新登录。")
    public Result<AdminLoginVO> changePassword(@Valid @RequestBody PasswordChangeReq req) {
        AdminAccount account = requireCurrentAccount();
        if (!passwordEncoder.matches(req.getOldPassword(), account.getPasswordHash())) {
            throw new BusinessException("当前密码不正确");
        }
        if (req.getOldPassword().equals(req.getNewPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        PasswordPolicy.assertStrong(req.getNewPassword());

        String clientIp = ClientIpUtil.resolveCurrent();
        adminAccountService.updatePassword(account.getId(), passwordEncoder.encode(req.getNewPassword()));
        AdminAccount refreshed = adminAccountService.findById(account.getId());
        if (refreshed == null) {
            throw new UnauthorizedException("管理端登录已失效，请重新登录");
        }
        securityAlertNotifier.notify(AlertType.PASSWORD_CHANGED, "管理端口令已修改（既有 token 全部失效）",
                "username=" + account.getUsername() + " · 来源 IP=" + clientIp);
        return Result.success(issueToken(refreshed, clientIp));
    }

    /**
     * 时间格式 —— 契约真源 {@code docs/api/web/auth.md}：{@code lastLoginAt} 为
     * {@code yyyy-MM-dd HH:mm:ss}。
     */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/me")
    @Operation(summary = "读取当前登录的管理员",
            description = "需带 Authorization: Bearer <token>。用于端上刷新页面时校验 token 是否仍有效，"
                    + "并据 role 隐显入口、据 passwordAging 给出安全提示。")
    public Result<AdminMeVO> me() {
        // 走到这里说明 AdminAuthFilter 已完成「取 token → 去 Bearer 前缀 → 验签 → 判过期 → 比对凭证版本」，
        // 并把账号 ID 作为 principal 放进了 SecurityContext —— 此处不重复验签，按 ID 回查账号即可。
        AdminAccount account = requireCurrentAccount();
        AdminMeVO vo = new AdminMeVO();
        vo.setUsername(account.getUsername());
        vo.setRole(account.getRole());
        LocalDateTime lastLoginAt = account.getLastLoginAt();
        vo.setLastLoginAt(lastLoginAt == null ? null : TIME_FORMATTER.format(lastLoginAt));
        vo.setPasswordAging(isPasswordAging(account));
        return Result.success(vo);
    }

    /**
     * 签发访问 token 并记录本次登录（刷新最近登录时间 + 推送「登录成功」告警）。
     *
     * <p>「登录成功即告警」是**必选**规则：单人管理端的每一次登录都应是本人发起，
     * 多出一次即失守信号 —— 这条告警是「撞对了密码」的唯一实时出口。
     */
    private AdminLoginVO issueToken(AdminAccount account, String clientIp) {
        String token = adminJwtUtil.createToken(
                account.getId(), account.getUsername(), account.getCredentialVersion());
        adminAccountService.touchLastLogin(account.getId());
        securityAlertNotifier.notify(AlertType.LOGIN_SUCCESS, "管理端登录成功",
                "username=" + account.getUsername() + " · 来源 IP=" + clientIp);

        AdminLoginVO vo = new AdminLoginVO();
        vo.setToken(token);
        vo.setUsername(account.getUsername());
        vo.setExpiresIn(adminJwtUtil.getExpirationSeconds());
        return vo;
    }

    /**
     * 记录一次登录失败：累加账号维度计数，首次达锁定阈值即告警
     * （同一轮持续爆破不再重复推送，避免告警通道被刷）。
     */
    private void recordLoginFailure(String username, String clientIp) {
        int failures = loginAttemptGuard.recordFailure(username);
        if (failures == LoginAttemptGuard.ALERT_THRESHOLD) {
            securityAlertNotifier.notify(AlertType.LOGIN_LOCKOUT, "管理端登录失败达锁定阈值（疑似暴力破解）",
                    "username=" + username + " · 连续失败 " + failures + " 次 · 来源 IP=" + clientIp);
        }
    }

    /**
     * 口令是否已超期（距上次改密 &gt;{@value #PASSWORD_AGING_DAYS} 天）。
     * <p>从未改密的账号以建号时间起算；两端都取不到时不提示（宁可少提示，不误报）。
     */
    private static boolean isPasswordAging(AdminAccount account) {
        LocalDateTime base = account.getPasswordChangedAt() != null
                ? account.getPasswordChangedAt() : account.getCreatedAt();
        return base != null && base.isBefore(LocalDateTime.now().minusDays(PASSWORD_AGING_DAYS));
    }

    /**
     * 取当前登录账号（自助端点共用）。
     *
     * @throws UnauthorizedException 账号已删除 / 已停用 —— 按会话失效处理
     */
    private AdminAccount requireCurrentAccount() {
        AdminAccount account = adminAccountService.findById(currentAccountId());
        if (account == null || !account.isActive()) {
            throw new UnauthorizedException("管理端登录已失效，请重新登录");
        }
        return account;
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

    /**
     * 登录退避：人为延迟。中断时恢复中断标志（不吞中断信号）。
     *
     * @param millis 延迟毫秒数；≤0 表示无需延迟
     */
    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
