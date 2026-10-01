package com.bjtufood.auth.config;

import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.ClientIpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 *   <li>仅作用 {@code /admin} 与 {@code /admin/**}（含管理端图片上传 {@code /admin/upload/image}），
 *       小程序端接口不受任何影响；校验通过后设置 ROLE_ADMIN 认证供授权层使用。</li>
 * </ul>
 * <p>
 * <b>本方案的结构性弱点（D4 登记，未根治）</b>——需要如实认识，不要误读为「管理端已加固」：
 * <ol>
 *   <li><b>单一共享凭据、无操作人身份</b>：口令一旦泄露即等于全量管理权限（可物理删除菜品并级联删除
 *       其全部评价、删除任意反馈与纠错，<b>不可逆</b>），且事后无法追溯操作人。
 *       本次已补的<b>审计日志</b>（见下方 doFilterInternal）只能记录「何时、从哪个 IP、调了哪个接口」，
 *       <b>不能回答「是谁」</b>——因为方案本身就没有身份概念。</li>
 *   <li><b>凭据一旦下发到浏览器即等同公开</b>：任何把口令放进前端产物的做法（本地 .env 注入、
 *       构建时内联、localStorage 存储）都会让「持有 DevTools 的人 = 持有全量管理权限」。
 *       当前 <b>Web 后台尚未接入该机制</b>（{@code web/src/api/http.ts} 只发 {@code Authorization: Bearer}，
 *       未发 {@code X-Admin-Token}；{@code VITE_ADMIN_TOKEN} 在 web 源码中零引用），
 *       故此风险<b>目前尚未成真</b>——但这也意味着管理端调 {@code /admin/**} 实际会直接 403。
 *       <b>一旦有人「把后台接通」，必须同时决定凭据分发方式</b>，否则等于主动引入上述风险。</li>
 * </ol>
 * <b>根治方向</b>（按投入递增，需产品/运维拍板，不在代码层自行决定）：
 * ① 部署侧限制管理端来源 IP（内网/VPN），口令只在内网可达；② 后端代理的一次性会话
 * （首次换短时 token，之后只带 token）；③ 换回真实管理员账号体系——本类已标
 * {@code @Deprecated(forRemoval=true)}，正是为 ③ 预留的。
 * <p>
 * 在此之前，本类能提供的确定性改进是：fail-closed、等时比较、路径口径统一、以及<b>操作留痕</b>。
 */
@Component
@Deprecated(since = "2026-09", forRemoval = true)
public class AdminTokenFilter extends OncePerRequestFilter {
    // ⚠️ 冻结：管理端（Web 后台）口令过滤器，待后期整体重构时移除。本期保留可编译、保留功能，不删除。

    /**
     * 管理端口令请求头 */
    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 管理端配置（类型化绑定，架构收口 P2；替代原先的 {@code @Value}）。
     * <p>
     * 「未配置即 fail-closed」的判据现由 {@link AdminProperties#isConfigured()} 承载，
     * 与「是否配置」成为同一份事实，不再是过滤器方法内联的判空逻辑。
     */
    private final AdminProperties adminProperties;

    private static final Logger log = LoggerFactory.getLogger(AdminTokenFilter.class);

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

    /**
     * 管理端根路径本身（无尾斜杠）。
     * <p>
     * 必须与 {@code ADMIN_PATH_PREFIX} 一起判：{@code SecurityConfig} 的白名单写的是 {@code "/admin/**"}，
     * 而 Spring 的 {@code /**} <b>同时匹配零段</b>，即 {@code /admin} 本身也在放行范围内。
     * 若过滤器只认 {@code "/admin/"}，那么「给 {@code @RequestMapping("/admin")} 的控制器加一个
     * 空路径端点」就会成为绕过口令校验的管理端入口——今天安全只是因为没有这样的端点。
     */
    private static final String ADMIN_PATH = "/admin";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 取「应用内路径」：getRequestURI() 含 context-path，减去 request.getContextPath() 即为应用内路径。
        String uri = request.getRequestURI();
        if (uri == null) {
            // 取不到 URI ⇒ **不跳过**过滤器（fail-closed）：宁可多校验一次口令，
            // 也不放行一个来源可疑的请求（此处旧行为是「跳过 = 放行」，方向正好相反）。
            return false;
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        // /admin 与 /admin/** 同等受口令保护（含管理端图片上传 /admin/upload/image）；
        // 学生端上传 /upload/cloud-image 走 JWT，不在本过滤器范围内。
        return !(ADMIN_PATH.equals(uri) || uri.startsWith(ADMIN_PATH_PREFIX));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!adminProperties.isConfigured()) {
            // fail-closed：未配置口令即拒绝，防止公网环境下的管理端裸奔
            log.warn("[ALERT] 管理端口令未配置，拒绝 /admin 请求（fail-closed）：method={} path={} ip={}",
                    request.getMethod(), request.getRequestURI(), ClientIpUtil.resolve(request));
            writeJson(response, HttpStatus.FORBIDDEN.value(),
                    Result.forbidden("管理端未配置 ADMIN_TOKEN，已拒绝访问（fail-closed）"));
            return;
        }
        String provided = request.getHeader(ADMIN_TOKEN_HEADER);
        if (!constantTimeEquals(adminProperties.getToken(), provided)) {
            // 失败审计：口令是「唯一凭据」，暴力猜解是本方案最现实的攻击面。
            // 只记方法/路径/IP，不记请求体与响应体——那些含用户内容，进日志无审计价值且污染检索。
            log.warn("[ALERT] 管理端口令校验失败：method={} path={} ip={}",
                    request.getMethod(), request.getRequestURI(), ClientIpUtil.resolve(request));
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
            // 审计（D4）：管理端具备<b>不可逆的破坏性操作</b>（物理删除菜品并级联删除其全部评价、
            // 删除任意反馈与纠错），而口令是<b>单一共享凭据、无操作人身份</b>——
            // 一旦凭据泄露，事后无任何线索可查（「谁删的」不可知）。故对全部管理端请求留痕。
            // 写操作（DELETE / PUT / POST）升为 WARN，读操作 DEBUG（避免列表翻页刷屏）。
            // 只记方法/路径/IP/结果码：路径已足以区分「删菜品」与「改反馈」，且不含任何用户内容。
            boolean mutating = !"GET".equals(request.getMethod())
                    && !"OPTIONS".equals(request.getMethod());
            if (mutating) {
                log.warn("[AUDIT] 管理端写操作：method={} path={} ip={} status={}",
                        request.getMethod(), request.getRequestURI(),
                        ClientIpUtil.resolve(request), response.getStatus());
            } else {
                log.debug("[AUDIT] 管理端读操作：method={} path={} ip={} status={}",
                        request.getMethod(), request.getRequestURI(),
                        ClientIpUtil.resolve(request), response.getStatus());
            }
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
