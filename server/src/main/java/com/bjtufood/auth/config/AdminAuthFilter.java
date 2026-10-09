package com.bjtufood.auth.config;

import com.bjtufood.auth.entity.AdminAccount;
import com.bjtufood.auth.service.AdminAccountService;
import com.bjtufood.auth.support.AdminJwtUtil;
import com.bjtufood.common.audit.AdminAuditRecorder;
import com.bjtufood.common.audit.AuditSnapshot;
import com.bjtufood.common.ratelimit.IpRateLimiter;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.ClientIpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 管理端鉴权过滤器（TD-20 · P0-4）—— {@code /admin/**} 的唯一鉴权入口。
 *
 * <p><b>校验对象</b>：请求头 {@code Authorization: Bearer <token>} 中的**管理端 JWT**，
 * 用<b>独立 secret</b>（{@code admin.jwt.secret}）验签 —— 与学生端 {@code spring.jwt.secret}
 * 不通用，学生 token 无法冒充管理端。
 *
 * <p><b>安全口径</b>：
 * <ul>
 *   <li><b>fail-closed（启动级）</b>：未配置 secret 时<b>启动即失败</b>
 *       （{@code AdminJwtUtil#validateSecretOnStartup}），而非「启动后所有请求静默通过」；</li>
 *   <li><b>fail-closed（请求级）</b>：缺 token / 签名无效 / 已过期 / 账号已停用（按 token 内
 *       账号 ID 回查 {@code admin_account.status}）→ 一律 <b>401</b>，绝不降级放行 ——
 *       C1「停用账号即刻生效」由此兑现；</li>
 *   <li><b>401 而非 403</b>：401 触发端上「清 token → 跳登录页」，403 不会 —— 这是端上
 *       会话失效处理的唯一依据；</li>
 *   <li>通过后设 {@code ROLE_ADMIN} 认证供授权层使用；请求结束清理上下文，避免线程复用残留。</li>
 * </ul>
 *
 * @see com.bjtufood.auth.controller.AdminAuthController 登录端点（公开）
 */
@Component
@RequiredArgsConstructor
public class AdminAuthFilter extends OncePerRequestFilter {

    /** 鉴权头名（与 {@code JwtAuthFilter} 同名不同源：本类只解析管理端 token） */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer 前缀（含尾部空格） */
    private static final String BEARER_PREFIX = "Bearer ";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AdminJwtUtil adminJwtUtil;

    /**
     * 账号状态回查（凭证吊销）：验签通过后按 token 内 ID 回查 {@code admin_account.status}，
     * 停用 / 不存在即 401。管理端请求低频（限频 60/分），逐请求回查成本可接受；
     * <b>不引入缓存</b> —— 状态一旦变更必须立即生效，缓存反而破坏「即刻生效」语义。
     */
    private final AdminAccountService adminAccountService;

    /**
     * IP 维度限频器（复用 {@code common.ratelimit.IpRateLimiter}，与其余公开写入口同一实现）。
     *
     * <p>🔴 <b>限频必须放在验签「之前」</b>：未鉴权的请求恰恰是攻击流量的主体，
     * 若置于其后，它们仍会逐条进过滤器、逐条打日志，线程与日志照样被刷。
     */
    private final IpRateLimiter ipRateLimiter;

    /**
     * 操作审计写入点：{@code /admin/**} 下的**写操作**（POST / PUT / DELETE / PATCH）落库。
     * <p>
     * 覆盖范围由「路径前缀 + HTTP 方法」判定 —— 新增写端点自动纳入，不存在「忘了挂审计」的漏挂面。
     * 审计写入失败不阻塞业务（见 {@link AdminAuditRecorder}）。
     */
    private final AdminAuditRecorder adminAuditRecorder;

    private static final Logger log = LoggerFactory.getLogger(AdminAuthFilter.class);

    /** User-Agent 头名（审计留痕用） */
    private static final String USER_AGENT_HEADER = "User-Agent";

    /** 角色取值（与 {@code admin_account.role} 一致） */
    private static final String ROLE_SUPER = "super";

    private static final String ROLE_OPERATOR = "operator";

    private static final String ROLE_VIEWER = "viewer";

    /** 只读方法（{@code viewer} 仅允许这些） */
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    /**
     * 写操作的 **token（adminId）维度**限频：20/分。
     * <p>
     * 阈值依据：管理端写操作是「一次改一条」的低频人工动作，正常使用**远低于** 20/分；
     * 而脚本批量删改会瞬间突破该值 —— 这正是「token 泄露后被滥用」的可观测信号。
     */
    private static final IpRateLimiter.Rule RULE_ADMIN_WRITE_PER_MINUTE = new IpRateLimiter.Rule(20, 60_000L);

    /** {@code /admin/**} 的 IP 限频规则（阈值按单人使用放宽，见 {@code P0-4 §4.7.4}） */
    private static final IpRateLimiter.Rule RULE_ADMIN_PER_MINUTE = new IpRateLimiter.Rule(60, 60_000L);

    private static final IpRateLimiter.Rule RULE_ADMIN_PER_HOUR = new IpRateLimiter.Rule(600, 3_600_000L);

    /** 鉴权失败日志采样：同一 IP 前 2 次记 WARN，第 3 次起降 DEBUG */
    private static final int FAIL_LOG_WARN_TIMES = 2;

    /** 失败计数表容量上限，超出后清理一次，防止异常流量下 key 无限增长 */
    private static final int MAX_FAIL_TRACK_ENTRIES = 10_000;

    /** ip -> 该 IP 累计鉴权失败次数（仅用于日志分级） */
    private final Map<String, AtomicInteger> failCounters = new ConcurrentHashMap<>();

    /** 管理端路径前缀（判定基准是 {@link #applicationPath} 剥离 context-path 之后的路径） */
    private static final String ADMIN_PATH_PREFIX = "/admin/";

    /** 管理端根路径本身（无尾斜杠）。Spring 的 {@code /**} 同时匹配零段，故 /admin 本身也在范围内 */
    private static final String ADMIN_PATH = "/admin";

    /**
     * <b>鉴权白名单</b>：登录端点（TD-19）。
     *
     * <p>🔴 <b>该路径必须跳过取 token 这一步</b>：登录请求自身还没有 token，若照常校验则
     * <b>必 401</b> ⇒ 「登录永远失败」，整个功能不可用。{@link com.bjtufood.auth.controller.AdminAuthController}
     * 的类注释点名了这个失效模式，本常量就是它的落地。
     *
     * <p>注意 {@link SecurityConfig} 里的 {@code permitAll("/admin/auth/login")} <b>管不到这里</b>：
     * 那是 Spring Security 授权层的白名单，而本过滤器在其<b>之前</b>执行 —— 两处白名单缺一不可。
     */
    private static final String LOGIN_PATH = "/admin/auth/login";

    /**
     * <b>登录第二步</b>（绑定 MFA 的账号）。
     *
     * <p>与 {@link #LOGIN_PATH} 同理：此时端上持有的是**第二因子票据**而非访问 token，
     * 若不在白名单内则必 401 ⇒ 绑定 MFA 的账号**永远登录不上**。
     */
    private static final String MFA_LOGIN_PATH = "/admin/auth/login/mfa";

    /**
     * <b>自助端点前缀</b>：{@code /admin/auth/**} 下作用于「当前登录者本人」的端点
     * （MFA 绑定 / 停用、改密）。
     *
     * <p>这类端点**跳过角色门控**：只读角色同样需要能绑定第二因子、能改自己的口令 ——
     * 卡住它们等于让低权限账号长期停留在「只有一层口令」的状态，与安全目标相反。
     * 但它们仍要求有效 token（走完整验签与账号状态回查）。
     */
    private static final String SELF_SERVICE_PREFIX = "/admin/auth/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 🔴 CORS 预检（OPTIONS）必须放行：预检**不携带任何凭证**，若照常校验则必 401，
        //    而 401 响应不含 CORS 头 ⇒ 浏览器判定「跨域被拦」，管理端全部 /admin 请求失败
        //    （2026-10-06 线上实测：OPTIONS /admin/auth/me → 401 无 ACAO）。预检无副作用，
        //    放行后由后续 CorsFilter 统一应答。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = applicationPath(request);
        // 取不到 URI ⇒ applicationPath 返回 null ⇒ **不跳过**（fail-closed）：
        // 宁可多校验一次，也不放行来源可疑的请求
        if (uri == null) {
            return false;
        }
        return !(ADMIN_PATH.equals(uri) || uri.startsWith(ADMIN_PATH_PREFIX));
    }

    /**
     * 应用内路径（已剥离 {@code context-path}）。
     *
     * <p>🔴 <b>必须按「URI 减 context-path」判定</b>：{@code getRequestURI()} 含 context-path
     * （如 {@code /api/v1/admin/dishes}），直接用 {@code startsWith("/admin/")} 判断在
     * 有前缀时<b>恒为 false</b> ⇒ 过滤器被跳过 ⇒ {@code /admin/**} 整体绕过鉴权。
     *
     * @return 应用内路径；URI 为 {@code null} 时返回 {@code null}（由调用方 fail-closed 处置）
     */
    private String applicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return null;
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = ClientIpUtil.resolve(request);

        // 🔴 IP 限频前置到验签之前（理由见字段注释）
        long waitSeconds = ipRateLimiter.tryAcquire(
                "admin-api", clientIp, RULE_ADMIN_PER_MINUTE, RULE_ADMIN_PER_HOUR);
        if (waitSeconds > 0) {
            // 已被限频的请求不写 WARN（否则日志正是被刷爆的地方），降为 DEBUG 留痕
            log.debug("[GUARD] /admin 请求被限频：method={} path={} ip={} retryAfter={}s",
                    request.getMethod(), request.getRequestURI(), clientIp, waitSeconds);
            writeJson(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                    Result.error("操作过于频繁，请 " + waitSeconds + " 秒后再试"));
            return;
        }

        // 🔴 白名单：登录端点自身还没有 token，照常校验必 401 ⇒「登录总是失败」。
        //    放行位置在**限频之后** —— /admin/** 全域 60/分 对登录同样成立，
        //    登录另有 5/分 · 20/时 的专属限频（在 Controller 内），两道各管各的，互不替代。
        //    第二步（票据）同理：端上此时持有的还不是访问 token。
        String appPath = applicationPath(request);
        if (LOGIN_PATH.equals(appPath) || MFA_LOGIN_PATH.equals(appPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            onAuthFailure(response, clientIp, request, "未携带管理端凭证");
            return;
        }

        Long accountId;
        Claims claims;
        try {
            // 🔴 取账号 ID 必须与验签同处一个 try：parseAndValidate 只保证「签名有效、未过期」，
            //    不保证 sub 可解析；畸形 sub（非数字 / 缺失）会在 getAccountId 抛
            //    IllegalArgumentException（NumberFormatException 的子类），留在 try 之外会冒泡成 500，
            //    而它本质上与「token 无效」是同一件事 ⇒ 同分支返回 401。
            claims = adminJwtUtil.parseAndValidate(token);
            accountId = adminJwtUtil.getAccountId(claims);
        } catch (JwtException | IllegalArgumentException e) {
            // 🔴 不回显异常细节（可能泄露签名算法 / 密钥长度等信息），统一归为「凭证无效」
            onAuthFailure(response, clientIp, request, "凭证无效或已过期");
            return;
        }

        // 🔴 凭证吊销（TD-25）：验签只证明「token 是我们签的」，不证明「账号仍启用」——
        //    停用（status=off）或已删除 ⇒ 401，兑现 C1「停用账号即刻生效」。
        AdminAccount account = adminAccountService.findById(accountId);
        if (account == null || !account.isActive()) {
            onAuthFailure(response, clientIp, request, "账号已停用或不存在");
            return;
        }

        // 🔴 凭证版本比对（改密即失效）：token 内嵌签发时的凭证版本，与库中现值不一致
        //    ⇒ 该 token 属于「改密前的旧凭证」，一律 401。缺该声明同样拒绝（fail-closed）。
        Integer tokenVersion = adminJwtUtil.getCredentialVersion(claims);
        if (tokenVersion == null || !tokenVersion.equals(account.getCredentialVersion())) {
            onAuthFailure(response, clientIp, request, "凭证版本已失效（口令已变更）");
            return;
        }

        // 🔴 角色门控（服务端强制，与前端隐显解耦）：viewer 只读；DELETE 仅 super。
        //    前端按角色隐显入口只是体验优化 —— 绕过前端直接调接口同样在此被拦。
        //    自助端点（改密 / MFA 绑定停用）例外：作用于本人账号，与角色无关。
        String role = normalizeRole(account.getRole());
        String httpMethodOfRequest = request.getMethod() == null
                ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        boolean selfService = appPath != null && appPath.startsWith(SELF_SERVICE_PREFIX);
        if (!selfService) {
            if (ROLE_VIEWER.equals(role) && !READ_METHODS.contains(httpMethodOfRequest)) {
                onForbidden(response, clientIp, request, "角色为只读，不可执行写操作");
                return;
            }
            if ("DELETE".equals(httpMethodOfRequest) && !ROLE_SUPER.equals(role)) {
                onForbidden(response, clientIp, request, "删除操作需要超级管理员权限");
                return;
            }
        }

        // 🔴 token 维度写限频：IP 闸管不住「同一来源下 token 被脚本滥用」——
        //    管理端「读多写极少」，写操作单独收紧既不误伤本人，又能当场掐断脚本批量删改。
        if (!READ_METHODS.contains(httpMethodOfRequest)) {
            long writeWaitSeconds = ipRateLimiter.tryAcquire("admin-api:write",
                    String.valueOf(accountId), RULE_ADMIN_WRITE_PER_MINUTE);
            if (writeWaitSeconds > 0) {
                log.warn("[ALERT] 管理端写操作被 token 维度限频：adminId={} method={} path={} ip={}",
                        accountId, httpMethodOfRequest, request.getRequestURI(), clientIp);
                writeJson(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                        Result.error("操作过于频繁，请 " + writeWaitSeconds + " 秒后再试"));
                return;
            }
        }

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                accountId, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_ADMIN_" + role.toUpperCase(Locale.ROOT)))));
        clearFailure(clientIp);
        String httpMethod = request.getMethod();
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 无状态体系：请求结束必须清理，避免容器线程复用导致认证残留
            SecurityContextHolder.clearContext();
            // 操作审计：写操作落库（读请求不落，避免审计表被浏览类请求刷爆）。
            // 变更前后值快照由 Service 侧登记在本线程上下文中，此处取走并落库（取走即清除）。
            if (adminAuditRecorder.isAuditable(httpMethod)) {
                String result = response.getStatus() < 400 ? "success" : "fail:" + response.getStatus();
                String[] snapshot = AuditSnapshot.take();
                adminAuditRecorder.record(accountId, httpMethod, applicationPath(request), result,
                        clientIp, request.getHeader(USER_AGENT_HEADER), snapshot[0], snapshot[1]);
            } else {
                // 读请求：清掉可能残留的登记（容器线程复用，绝不跨请求带出）
                AuditSnapshot.clear();
            }
        }
    }

    /**
     * 从 {@code Authorization} 头取出 token。
     *
     * @return token；头缺失或不是 {@code Bearer } 格式时返回 {@code null}
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 鉴权失败：**401** + 采样日志。
     *
     * <p>🔴 用 <b>401</b> 而非 403 —— 端上靠 401 触发「清 token 跳登录页」；
     * 若用 403，端上不会重新登录，用户会卡在「一直无权限」的界面。
     */
    private void onAuthFailure(HttpServletResponse response, String clientIp,
                               HttpServletRequest request, String reason) throws IOException {
        int failures = recordFailure(clientIp);
        if (failures <= FAIL_LOG_WARN_TIMES) {
            log.warn("[ALERT] 管理端鉴权失败（{}）：method={} path={} ip={}（该 IP 累计 {} 次）",
                    reason, request.getMethod(), request.getRequestURI(), clientIp, failures);
        } else {
            log.debug("[ALERT] 管理端鉴权失败（已采样，第 {} 次起降级）：method={} path={} ip={}",
                    failures, request.getMethod(), request.getRequestURI(), clientIp);
        }
        writeJson(response, HttpStatus.UNAUTHORIZED.value(),
                Result.unauthorized("管理端登录已失效，请重新登录"));
    }

    /**
     * 角色不足：**403** + WARN 留痕。
     *
     * <p>🔴 用 <b>403</b> 而非 401 —— 401 会让端上「清 token 跳登录页」，而此处身份**有效**、
     * 仅权限不足；用 401 会把用户无谓地踢出登录（属错误处置）。403 对端上是「提示无权限」，
     * 不触发重新登录。
     *
     * <p>越权尝试本身是**安全信号**：一律 WARN（含角色之外的路径与来源 IP），便于事后检索。
     */
    private void onForbidden(HttpServletResponse response, String clientIp,
                             HttpServletRequest request, String reason) throws IOException {
        log.warn("[ALERT] 管理端越权尝试（{}）：method={} path={} ip={}",
                reason, request.getMethod(), request.getRequestURI(), clientIp);
        writeJson(response, HttpStatus.FORBIDDEN.value(), Result.forbidden("无权限执行该操作"));
    }

    /**
     * 角色归一化：**未知 / 空值一律按只读（{@code viewer}）处理**（fail-closed）——
     * 角色数据异常时宁可少给权限，绝不多给。
     */
    private static String normalizeRole(String role) {
        if (role == null) {
            return ROLE_VIEWER;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case ROLE_SUPER, ROLE_OPERATOR -> normalized;
            default -> ROLE_VIEWER;
        };
    }

    /**
     * 记录一次鉴权失败并返回该 IP 的累计失败次数（供日志采样判级）。
     * <p>
     * 🔴 <b>容量兜底</b>：与 {@code IpRateLimiter} 同款思路 —— 异常流量下 key 不得无限增长；
     * 超出上限时先清空一次再记（此表仅用于日志分级，丢失计数无正确性影响）。
     */
    private int recordFailure(String ip) {
        if (failCounters.size() > MAX_FAIL_TRACK_ENTRIES) {
            failCounters.clear();
        }
        return failCounters.computeIfAbsent(ip, k -> new AtomicInteger()).incrementAndGet();
    }

    /** 鉴权通过后清零该 IP 的失败计数（避免旧计数影响后续日志分级） */
    private void clearFailure(String ip) {
        failCounters.remove(ip);
    }

    private static void writeJson(HttpServletResponse response, int status, Result<?> body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }
}
