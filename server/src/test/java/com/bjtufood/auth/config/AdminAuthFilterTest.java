package com.bjtufood.auth.config;

import com.bjtufood.auth.entity.AdminAccount;
import com.bjtufood.auth.service.AdminAccountService;
import com.bjtufood.auth.support.AdminJwtUtil;
import com.bjtufood.common.ratelimit.IpRateLimiter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AdminAuthFilter}（管理端 JWT 鉴权，TD-20）单元测试。
 *
 * <p><b>覆盖两条主线</b>：① <b>路径作用域的 context-path 无关性</b>（防越权的关键）；
 * ② <b>IP 限频与按 IP 隔离</b>（TD-22，防狂打拖垮全站）。
 *
 * <p><b>存在理由（路径作用域）</b>：若用 {@code getRequestURI().contains("/admin/")} 判断作用域，
 * 这在 {@code context-path=/api} 时属于「碰巧命中」，一旦 context-path 升版为 {@code /api/v1}
 * 或出现其他前缀，就可能<b>漏检 → /admin/** 整体绕过鉴权（严重越权）</b>。
 * 该风险单靠 {@code SmokeApiTest} 覆盖不到：MockMvc 默认 {@code contextPath} 为空，
 * 无论前缀怎么变都命中，测不出「前缀变化」这一真正的失效场景。
 *
 * <p>故本测试直接构造带 {@code contextPath} 的 {@link MockHttpServletRequest}，
 * 把「context-path 无关性」显式锁死——这是 {@code /api/v1} 迁移的安全前置条件。
 */
class AdminAuthFilterTest {

    /** 管理端 JWT 签名密钥（测试用；长度满足 HMAC-SHA 要求） */
    private static final String SECRET = "AdminAuthFilterTestSecretKey_0123456789ABCDEF";

    /** 受控配置对象 */
    private static AdminProperties props() {
        AdminProperties p = new AdminProperties();
        p.getJwt().setSecret(SECRET);
        p.getJwt().setExpirationSeconds(3600L);
        return p;
    }

    /** 被测过滤器（账号回查桩：恒返回启用账号） */
    private AdminAuthFilter filter() {
        return new AdminAuthFilter(new AdminJwtUtil(props()), accountService(true), new IpRateLimiter(),
                auditRecorder());
    }

    /**
     * 审计记录器（真实实例 + 打桩其依赖）：本测试只关心鉴权与限频两条主线，
     * 审计写入落到 mock 的 Mapper 上，不触碰数据库。
     */
    private static com.bjtufood.common.audit.AdminAuditRecorder auditRecorder() {
        return new com.bjtufood.common.audit.AdminAuditRecorder(
                org.mockito.Mockito.mock(com.bjtufood.common.audit.mapper.AdminAuditLogMapper.class),
                org.mockito.Mockito.mock(com.bjtufood.common.alert.SecurityAlertNotifier.class));
    }

    /** 账号回查桩：{@code enabled=true} 返回启用账号，否则停用账号（TD-25 凭证吊销用例） */
    private AdminAccountService accountService(boolean enabled) {
        return new AdminAccountService() {
            @Override
            public AdminAccount findByUsername(String username) {
                return account(enabled);
            }

            @Override
            public AdminAccount findById(Long id) {
                return account(enabled);
            }

            @Override
            public void touchLastLogin(Long accountId) {
                // no-op
            }



            @Override
            public void updatePassword(Long accountId, String passwordHash) {
                // no-op
            }
        };
    }

    private AdminAccount account(boolean enabled) {
        AdminAccount a = new AdminAccount();
        a.setId(1L);
        a.setUsername("kingdo404");
        a.setStatus(enabled ? "on" : "off");
        // 凭证版本须与 token 内嵌值一致，否则按「改密后旧凭证」拒绝
        a.setCredentialVersion(1);
        return a;
    }

    /** 造一个带合法 token 的请求 */
    private MockHttpServletRequest authed(String path) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        req.addHeader("Authorization", "Bearer " + token());
        return req;
    }

    /**
     * 造一个**带 contextPath** 的请求 —— 这是本测试类的核心手法。
     *
     * <p>{@code getRequestURI()} 返回的是<b>含 context-path 的完整路径</b>（如
     * {@code /api/v1/admin/dishes}），而过滤器的判定基准是<b>应用内路径</b>（已剥离 prefix）。
     * 只在 URI 字符串里拼前缀而<b>不设 contextPath</b>，则 {@code getContextPath()} 返回空串、
     * 前缀无法被剥离 —— 测的就不是「有 context-path」这个真实场景了。
     */
    private MockHttpServletRequest req(String contextPath, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setContextPath(contextPath);
        return req;
    }

    /** 造一个 token（与 filter() 用同一份密钥配置） */
    private String token() {
        return new AdminJwtUtil(props()).createToken(1L, "kingdo404", 1);
    }

    /**
     * 造一个<b>签名合法但载荷非法</b>的 token：{@code sub} 直接写成传入的任意串。
     *
     * <p>不能复用 {@link AdminJwtUtil#createToken} —— 它把账号 ID 转成字符串写 {@code sub}，
     * 造不出「非数字 sub」这一形态，而该形态正是下面用例要锁死的失效场景。
     */
    private String tokenWithSubject(String subject) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim("username", "kingdo404")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    // ==================== 路径作用域（context-path 无关性）====================

    @Test
    @DisplayName("shouldNotFilter：/admin/** 在任意 context-path 下都必须「不过滤」（即受鉴权保护）")
    void adminPathsAreProtectedUnderAnyContextPath() {
        AdminAuthFilter f = filter();
        for (String ctx : new String[]{"", "/api", "/api/v1", "/deep/prefix"}) {
            assertThat(f.shouldNotFilter(req(ctx, ctx + "/admin/dishes")))
                    .as("contextPath=%s 下 /admin/dishes 必须受保护（shouldNotFilter 应为 false）", ctx)
                    .isFalse();
            assertThat(f.shouldNotFilter(req(ctx, ctx + "/admin/upload")))
                    .as("contextPath=%s 下 /admin/upload 必须受保护", ctx)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("shouldNotFilter：非 /admin 路径在任意 context-path 下都应跳过过滤器")
    void nonAdminPathsSkipFilterUnderAnyContextPath() {
        AdminAuthFilter f = filter();
        for (String ctx : new String[]{"", "/api", "/api/v1"}) {
            for (String path : new String[]{"dishes", "auth/wechat-login", "upload/cloud-image"}) {
                assertThat(f.shouldNotFilter(req(ctx, ctx + "/" + path)))
                        .as("contextPath=%s 下 %s 应跳过过滤器", ctx, path)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("shouldNotFilter：形如 /x/admin/ 的「伪管理端」路径不得被误判为管理端")
    void lookalikePathIsNotTreatedAsAdmin() {
        AdminAuthFilter f = filter();
        assertThat(f.shouldNotFilter(new MockHttpServletRequest("GET", "/x/admin/dishes"))).isTrue();
    }

    @Test
    @DisplayName("shouldNotFilter：精确路径 /admin（无尾斜杠）同样受保护（白名单 /admin/** 匹配零段）")
    void exactAdminPathIsProtected() {
        AdminAuthFilter f = filter();
        for (String ctx : new String[]{"", "/api", "/api/v1"}) {
            assertThat(f.shouldNotFilter(req(ctx, ctx + "/admin")))
                    .as("contextPath=%s 下 /admin（无尾斜杠）必须受保护——否则给它加一个空路径端点即成越权入口", ctx)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("shouldNotFilter：/adminx 这类同前缀但非管理端路径不得被误判")
    void similarPrefixIsNotAdmin() {
        AdminAuthFilter f = filter();
        assertThat(f.shouldNotFilter(new MockHttpServletRequest("GET", "/adminx/dishes"))).isTrue();
    }

    @Test
    @DisplayName("shouldNotFilter：取不到 URI 时不跳过过滤器（fail-closed）")
    void nullUriFailsClosed() {
        AdminAuthFilter f = filter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/x");
        req.setRequestURI(null);
        assertThat(f.shouldNotFilter(req)).as("取不到 URI 必须按「不过滤」处理（fail-closed）").isFalse();
    }
    // ==================== 鉴权行为 ====================

    @Test
    @DisplayName("未携带 Authorization → 401，且不进入后续过滤器链")
    void missingAuthorizationIsUnauthorized() throws ServletException, IOException {
        AdminAuthFilter f = filter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin/dishes");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean[] reachedChain = {false};
        f.doFilter(req, resp, (r, s) -> reachedChain[0] = true);
        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(reachedChain[0]).as("未鉴权请求不得触达业务链路").isFalse();
    }

    @Test
    @DisplayName("token 无效 / 被篡改 → 401（不回显异常细节）")
    void tamperedTokenIsUnauthorized() throws ServletException, IOException {
        AdminAuthFilter f = filter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin/dishes");
        req.addHeader("Authorization", "Bearer not-a-real-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean[] reachedChain = {false};
        f.doFilter(req, resp, (r, s) -> reachedChain[0] = true);
        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(reachedChain[0]).isFalse();
    }

    @Test
    @DisplayName("载荷畸形：签名合法但 sub 非数字 → 401（不得冒泡成 500）")
    void malformedSubjectIsUnauthorized() throws ServletException, IOException {
        AdminAuthFilter f = filter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin/dishes");
        req.addHeader("Authorization", "Bearer " + tokenWithSubject("abc"));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean[] reachedChain = {false};

        f.doFilter(req, resp, (r, s) -> reachedChain[0] = true);

        // 验签只证明「token 是我们签的」，不保证 sub 可解析：Long.valueOf("abc") 抛 NumberFormatException。
        // 它与「签名无效」是同一件事——凭证不可用 ⇒ 必须 401（清 token 跳登录页），不能冒泡成 500
        // （500 会让端上读成「服务端故障」而保留坏 token，陷入反复失败的死循环）。
        assertThat(resp.getStatus()).as("畸形 sub 必须归入「凭证无效」分支").isEqualTo(401);
        assertThat(reachedChain[0]).as("载荷非法的请求不得触达业务链路").isFalse();
    }

    @Test
    @DisplayName("TD-25：停用账号的已签发 token → 401（停用即刻生效，链路不得到达）")
    void disabledAccountTokenIsUnauthorized() throws ServletException, IOException {
        AdminAuthFilter f = new AdminAuthFilter(new AdminJwtUtil(props()), accountService(false), new IpRateLimiter(),
                auditRecorder());
        MockHttpServletRequest req = authed("/admin/dishes");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean[] reachedChain = {false};
        f.doFilter(req, resp, (r, s) -> reachedChain[0] = true);
        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(reachedChain[0]).isFalse();
    }

    @Test
    @DisplayName("合法 token → 放行并设置 ROLE_ADMIN，链路结束后清理上下文")
    void validTokenSetsAdminRoleAndClearsContext() throws ServletException, IOException {
        AdminAuthFilter f = filter();
        MockHttpServletRequest ok = authed("/admin/dishes");
        MockHttpServletResponse okResp = new MockHttpServletResponse();
        String[] capturedRole = {null};
        f.doFilter(ok, okResp, (r, s) -> capturedRole[0] =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication().getAuthorities().stream()
                        .map(Object::toString).reduce("", (a, b) -> a + b));

        assertThat(okResp.getStatus()).isEqualTo(200);
        assertThat(capturedRole[0]).contains("ROLE_ADMIN");
        // 无状态体系：链路结束必须清理，避免容器线程复用导致认证残留
        assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication())
                .as("请求结束后必须清理 SecurityContext")
                .isNull();
    }

    // ==================== 鉴权白名单（TD-19 · 登录端点自身）====================

    @Test
    @DisplayName("白名单：POST /admin/auth/login 不带 token 也必须放行（否则登录请求自身被拦 ⇒ 登录永远 401）")
    void loginEndpointIsWhitelistedWithoutToken() throws ServletException, IOException {
        AdminAuthFilter f = filter();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/admin/auth/login");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean[] reached = {false};

        f.doFilter(req, resp, (r, s) -> reached[0] = true);

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(reached[0]).as("登录端点必须触达业务链路").isTrue();
    }

    @Test
    @DisplayName("白名单按「应用内路径」判定：带 context-path 的登录同样放行")
    void loginWhitelistIsContextPathAgnostic() throws ServletException, IOException {
        AdminAuthFilter f = filter();
        for (String ctx : new String[]{"/api", "/api/v1", "/deep/prefix"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", ctx + "/admin/auth/login");
            req.setContextPath(ctx);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean[] reached = {false};

            f.doFilter(req, resp, (r, s) -> reached[0] = true);

            assertThat(resp.getStatus()).as("contextPath=%s 下登录应放行", ctx).isEqualTo(200);
            assertThat(reached[0]).as("contextPath=%s 下登录应触达业务链路", ctx).isTrue();
        }
    }

    @Test
    @DisplayName("白名单作用域只到登录端点为止：/admin/auth/me 不带 token 仍 401")
    void whitelistDoesNotLeakToOtherAdminPaths() throws ServletException, IOException {
        AdminAuthFilter f = filter();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean[] reached = {false};

        f.doFilter(new MockHttpServletRequest("GET", "/admin/auth/me"), resp, (r, s) -> reached[0] = true);

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(reached[0]).as("非白名单端点不得绕过鉴权").isFalse();
    }

    // ==================== IP 限频（TD-22）====================

    @Test
    @DisplayName("TD-22：/admin/** 单 IP 超阈值后被限频拦截")
    void adminApiIsIpRateLimited() throws ServletException, IOException {
        AdminAuthFilter f = filter();
        MockHttpServletRequest probe = authed("/admin/dishes");
        probe.setRemoteAddr("10.1.2.3");

        // 阈值 60/分钟：前 60 次放行
        for (int i = 0; i < 60; i++) {
            MockHttpServletRequest req = authed("/admin/dishes");
            req.setRemoteAddr("10.1.2.3");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean[] passed = {false};
            f.doFilter(req, resp, (r, s) -> passed[0] = true);
            assertThat(passed[0]).as("第 " + (i + 1) + " 次应在阈值内放行").isTrue();
        }

        // 第 61 次：限频拦截 —— 直接写 429 JSON（不抛异常，与鉴权失败同款「过滤器内直接写出」口径），
        // 且**不进入业务链路**
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        boolean[] reachedChain = {false};
        f.doFilter(probe, blocked, (r, s) -> reachedChain[0] = true);
        assertThat(blocked.getStatus()).as("超阈值应返回 429").isEqualTo(429);
        assertThat(blocked.getContentAsString()).as("429 响应体应含限频提示").contains("操作过于频繁");
        assertThat(reachedChain[0]).as("超阈值请求不得触达业务链路").isFalse();
    }

    @Test
    @DisplayName("TD-22：限频按 IP 隔离 —— 换一个 IP 不受影响（单人使用不被误伤）")
    void rateLimitIsScopedPerIp() throws ServletException, IOException {
        AdminAuthFilter f = filter();
        for (int i = 0; i < 60; i++) {
            MockHttpServletRequest req = authed("/admin/dishes");
            req.setRemoteAddr("10.1.2.3");
            f.doFilter(req, new MockHttpServletResponse(), (r, s) -> { });
        }
        // 另一个 IP（换网络出口）仍应放行
        MockHttpServletRequest other = authed("/admin/dishes");
        other.setRemoteAddr("192.168.9.9");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean[] reachedChain = {false};
        f.doFilter(other, resp, (r, s) -> reachedChain[0] = true);
        assertThat(reachedChain[0]).as("不同 IP 的额度相互独立").isTrue();
    }
}
