package com.bjtufood.auth.config;

import com.bjtufood.auth.entity.AdminAccount;
import com.bjtufood.auth.service.AdminAccountService;
import com.bjtufood.auth.support.AdminJwtUtil;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 管理端鉴权过滤器（TD-20 · P0-4）—— <b>取代</b> {@link AdminTokenFilter} 的静态口令校验。
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

    private static final Logger log = LoggerFactory.getLogger(AdminAuthFilter.class);

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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
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
        if (LOGIN_PATH.equals(applicationPath(request))) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            onAuthFailure(response, clientIp, request, "未携带管理端凭证");
            return;
        }

        Claims claims;
        try {
            claims = adminJwtUtil.parseAndValidate(token);
        } catch (JwtException | IllegalArgumentException e) {
            // 🔴 不回显异常细节（可能泄露签名算法 / 密钥长度等信息），统一归为「凭证无效」
            onAuthFailure(response, clientIp, request, "凭证无效或已过期");
            return;
        }

        Long accountId = adminJwtUtil.getAccountId(claims);

        // 🔴 凭证吊销（TD-25）：验签只证明「token 是我们签的」，不证明「账号仍启用」——
        //    停用（status=off）或已删除 ⇒ 401，兑现 C1「停用账号即刻生效」。
        AdminAccount account = adminAccountService.findById(accountId);
        if (account == null || !account.isActive()) {
            onAuthFailure(response, clientIp, request, "账号已停用或不存在");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                accountId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        clearFailure(clientIp);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 无状态体系：请求结束必须清理，避免容器线程复用导致认证残留
            SecurityContextHolder.clearContext();
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