package com.bjtufood.auth.config;

import com.bjtufood.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 管理端（Web 后台）口令校验过滤器。
 * <p>
 * 背景：小程序端已无管理员登录，Web 后台不再做账号登录（登录即用 / 无感），但后端部署在公网，
 * 因此管理端接口改由**环境变量口令**保护：请求头 {@code X-Admin-Token} 必须等于环境变量
 * {@code ADMIN_TOKEN}（{@code admin.token}）。Web 侧在本地 .env 配同一个口令，启动时自动携带，用户无感。
 * <p>
 * 安全口径：
 * <ul>
 *   <li>未配置 {@code ADMIN_TOKEN} → **fail-closed 拒绝全部 /admin 请求**（403），避免遗忘配置导致管理端裸奔；</li>
 *   <li>口令比对使用等时比较（MessageDigest.isEqual），降低时序侧信道风险；</li>
 *   <li>仅作用 {@code /admin/**}（含管理端图片上传 {@code /admin/upload/image}），
 *       小程序端接口不受任何影响；校验通过后设置 ROLE_ADMIN 认证供授权层使用。</li>
 * </ul>
 */
@Component
@Deprecated(since = "2026-09", forRemoval = true)
public class AdminTokenFilter extends OncePerRequestFilter {
    // ⚠️ 冻结：管理端（Web 后台）口令过滤器，待后期整体重构时移除。本期保留可编译、保留功能，不删除。

    /** 管理端口令请求头 */
    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 管理端口令配置（类型化绑定，架构收口 P2；替代原先的 {@code @Value}）。
     * <p>
     * 「未配置即 fail-closed」的判据现由 {@link AdminProperties#isConfigured()} 承载，
     * 与「是否配置」成为同一份事实，不再是过滤器方法内联的判空逻辑。
     */
    private final AdminProperties adminProperties;

    public AdminTokenFilter(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    /**
     * 管理端路径前缀（应用内路径口径，<b>不含 context-path</b>）。
     * <p>
     * <b>为何手写而不用 {@code AntPathRequestMatcher} / {@code getServletPath()}</b>（均为实测踩坑）：
     * <ol>
     *   <li>{@code request.getServletPath()}：MockMvc 的 {@code MockHttpServletRequest} <b>不填 servletPath</b>
     *       （为 {@code null}），据此判断会让切片测试整体跳过过滤器——本次改造实测 3 个 admin 用例失守；</li>
     *   <li>{@code new AntPathRequestMatcher("/admin/**)}：Spring Security 6.2.0 的单参构造未注入
     *       {@code UrlPathHelper}，内部回退到 {@code getServletPath()}，同样在 MockMvc 下失效
     *       （实测 DIAG 显示 {@code getPathWithinApplication} 已正确得到 {@code /admin/feedbacks}，
     *       但 {@code matches()} 仍返回 false）；</li>
     *   <li>{@code getRequestURI().contains("/admin/")}（原实现）：<b>安全缺陷</b>——它匹配的是含
     *       context-path 的全量 URI，且用 {@code contains} 子串判断；context-path 升版为 {@code /api/v1}
     *       或路径形态变化即可能漏检，导致 {@code /admin/**} 绕过口令校验。</li>
     * </ol>
     * 现采用显式「URI 减 contextPath」——语义与 {@code SecurityConfig} 的白名单口径一致，
     * 且在真实容器与 MockMvc 下<b>行为完全相同</b>；该等价性由
     * {@code AdminTokenFilterTest} 在多种 context-path 下锁定。
     */
    private static final String ADMIN_PATH_PREFIX = "/admin/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 取「应用内路径」：getRequestURI() 含 context-path，减去 request.getContextPath() 即为应用内路径。
        String uri = request.getRequestURI();
        if (uri == null) {
            return true;
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        // /admin/** 全量受口令保护（含管理端图片上传 /admin/upload/image）；
        // 学生端上传 /upload/cloud-image 走 JWT，不在本过滤器范围内。
        return !uri.startsWith(ADMIN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!adminProperties.isConfigured()) {
            // fail-closed：未配置口令即拒绝，防止公网环境下的管理端裸奔
            writeJson(response, HttpStatus.FORBIDDEN.value(),
                    Result.forbidden("管理端未配置 ADMIN_TOKEN，已拒绝访问（fail-closed）"));
            return;
        }
        String provided = request.getHeader(ADMIN_TOKEN_HEADER);
        if (!constantTimeEquals(adminProperties.getToken(), provided)) {
            writeJson(response, HttpStatus.FORBIDDEN.value(), Result.forbidden("管理端口令无效"));
            return;
        }
        // 口令校验通过后补设 Authentication（ROLE_ADMIN）：使请求能通过 SecurityConfig 的
        // anyRequest().authenticated() 授权检查（/upload/image 已不在 permitAll 白名单，B4）。
        // 否则授权层因匿名身份返回 401，即使口令正确后台上传也会失败。
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 无状态体系：请求结束后清理上下文，避免容器线程复用导致的认证残留
            SecurityContextHolder.clearContext();
        }
    }

    /** 等时比较，避免通过响应时间差逐字符猜测口令 */
    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeJson(HttpServletResponse response, int status, Result<?> body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }
}
