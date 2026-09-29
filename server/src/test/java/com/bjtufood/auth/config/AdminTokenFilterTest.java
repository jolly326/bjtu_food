package com.bjtufood.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AdminTokenFilter} 路径作用域与 fail-closed 行为的单元测试。
 * <p>
 * <b>存在理由（2026-09-28 架构收口）</b>：本类历史上用
 * {@code request.getRequestURI().contains("/admin/")} 判断作用域——这在
 * {@code context-path=/api} 时属于「碰巧命中」，一旦 context-path 升版为
 * {@code /api/v1} 或出现其他前缀，就可能<b>漏检 → /admin/** 绕过口令校验（严重越权）</b>。
 * 该风险单靠 {@code SmokeApiTest} 覆盖不到：MockMvc 默认 {@code contextPath} 为空，
 * 无论前缀怎么变都命中，测不出「前缀变化」这一真正的失效场景。
 * <p>
 * 故本测试直接构造带 {@code contextPath} 的 {@link MockHttpServletRequest}，
 * 把「context-path 无关性」显式锁死——这是 {@code /api/v1} 迁移的安全前置条件。
 */
class AdminTokenFilterTest {

    private static final String TOKEN = "test-admin-token";

    /** 受控配置对象（2026-09-28 架构收口 P2：配置由 AdminProperties 承载，不再是过滤器内的 @Value 字段） */
    private static AdminProperties props() {
        AdminProperties p = new AdminProperties();
        p.setToken(TOKEN);
        return p;
    }

    private AdminTokenFilter filter() {
        return new AdminTokenFilter(props());
    }

    // ==================== 作用域判定（context-path 无关性）====================

    @Test
    @DisplayName("shouldNotFilter：/admin/** 在任意 context-path 下都必须「不过滤」（即受口令保护）")
    void adminPathsAreProtectedUnderAnyContextPath() {
        AdminTokenFilter f = filter();

        for (String ctx : new String[]{"", "/api", "/api/v1", "/deep/nested/context"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", ctx + "/admin/feedbacks");
            req.setContextPath(ctx);

            assertThat(f.shouldNotFilter(req))
                    .as("contextPath=%s 下 /admin/feedbacks 必须受保护（shouldNotFilter 应为 false）", ctx)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("shouldNotFilter：非 /admin 路径在任意 context-path 下都应跳过过滤器")
    void nonAdminPathsSkipFilterUnderAnyContextPath() {
        AdminTokenFilter f = filter();

        for (String ctx : new String[]{"", "/api", "/api/v1"}) {
            for (String path : new String[]{"/dishes", "/dishes/1", "/upload/cloud-image",
                    "/feedback", "/auth/wechat-login", "/my/reviews"}) {
                MockHttpServletRequest req = new MockHttpServletRequest("POST", ctx + path);
                req.setContextPath(ctx);

                assertThat(f.shouldNotFilter(req))
                        .as("contextPath=%s 下 %s 应跳过过滤器（shouldNotFilter 应为 true）", ctx, path)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("shouldNotFilter：形如 /x/admin/ 的「伪管理端」路径不得被误判为管理端")
    void lookalikePathIsNotTreatedAsAdmin() {
        AdminTokenFilter f = filter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/dishes/admin/1");
        req.setContextPath("/api");

        // 原 contains 写法会把「含 /admin/ 子串」的任何路径都纳入口令保护（过度拦截）
        assertThat(f.shouldNotFilter(req)).isTrue();
    }

    // ==================== fail-closed 行为 ====================

    @Test
    @DisplayName("未配置 ADMIN_TOKEN → fail-closed 403，且不进入后续过滤器链")
    void unconfiguredTokenFailsClosed() throws ServletException, IOException {
        // 传入空口令的配置对象——AdminProperties.isConfigured() 即为唯一 fail-closed 判据
        AdminProperties empty = new AdminProperties();
        empty.setToken("");
        AdminTokenFilter f = new AdminTokenFilter(empty);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/feedbacks");
        req.setContextPath("/api/v1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean[] chainCalled = {false};

        f.doFilter(req, resp, (r, s) -> chainCalled[0] = true);

        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentAsString()).contains("管理端未配置");
        assertThat(chainCalled[0]).as("未配置口令时不得放行到后续链路").isFalse();
    }

    @Test
    @DisplayName("口令错误 → 403；口令正确 → 放行并设置 ROLE_ADMIN，链路结束后清理上下文")
    void tokenMismatchForbiddenAndSuccessSetsAdminRole() throws ServletException, IOException {
        AdminTokenFilter f = filter();

        // 口令错误
        MockHttpServletRequest bad = new MockHttpServletRequest("GET", "/api/v1/admin/feedbacks");
        bad.setContextPath("/api/v1");
        bad.addHeader(AdminTokenFilter.ADMIN_TOKEN_HEADER, "wrong-token");
        MockHttpServletResponse badResp = new MockHttpServletResponse();
        f.doFilter(bad, badResp, (r, s) -> {
        });
        assertThat(badResp.getStatus()).isEqualTo(403);
        assertThat(badResp.getContentAsString()).contains("管理端口令无效");

        // 口令正确
        MockHttpServletRequest ok = new MockHttpServletRequest("GET", "/api/v1/admin/feedbacks");
        ok.setContextPath("/api/v1");
        ok.addHeader(AdminTokenFilter.ADMIN_TOKEN_HEADER, TOKEN);
        MockHttpServletResponse okResp = new MockHttpServletResponse();
        String[] capturedRole = {null};
        f.doFilter(ok, okResp, (r, s) -> capturedRole[0] = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getAuthorities().stream()
                .map(Object::toString).reduce("", (a, b) -> a + b));

        assertThat(okResp.getStatus()).isEqualTo(200);
        assertThat(capturedRole[0]).contains("ROLE_ADMIN");
        // 无状态体系：链路结束必须清理，避免容器线程复用导致认证残留
        assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication())
                .as("请求结束后必须清理 SecurityContext")
                .isNull();
    }
}
