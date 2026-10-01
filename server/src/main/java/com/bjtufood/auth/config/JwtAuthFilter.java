package com.bjtufood.auth.config;

import com.bjtufood.auth.support.JwtUtil;
import com.bjtufood.common.config.CorsProperties;
import com.bjtufood.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 认证过滤器
 * <p>
 * 继承 OncePerRequestFilter，确保每个请求只执行一次。
 * 从请求头 Authorization 中提取 JWT Token，校验并解析用户信息，
 * 设置到 Spring Security 的 SecurityContext 中。
 * <p>
 * 过滤器链顺序：
 * 1. 所有请求进入此过滤器
 * 2. 检查是否携带 Token
 * 3. 有 Token → 校验 → 设置认证信息 → 放行
 * 4. 无 Token → 直接放行（留给 Controller 的 @PreAuthorize 做权限控制）
 * <p>
 * SecurityContext 中存储的自定义信息可通过工具类获取：
 * <pre>
 * Long userId = (Long) SecurityContextHolder.getContext()
 *     .getAuthentication().getDetails();
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklist tokenBlacklist;
    /**
     * CORS 受信任源，与 {@code CorsConfig} <b>共用同一份配置与同一段解析</b>。
     * <p>
     * 这里的 Origin 校验是 CSRF 兜底，判据必须与浏览器侧 CORS 放行口径逐字一致：历史实现两处各自
     * {@code @Value} 绑定，且对「未配置白名单」的处理相反（CorsConfig 放行 {@code Origin: null}，
     * 本类拒绝），会出现「预检放行、实际请求 403」这类自相矛盾的行为。现统一为一处。
     */
    private final CorsProperties corsProperties;

    /** 统一错误响应出口用的 ObjectMapper（与 AdminTokenFilter / SecurityConfig 同口径） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 请求头中 Token 的前缀 */
    private static final String TOKEN_PREFIX = "Bearer ";

    /** 请求头名称 */
    private static final String HEADER_NAME = "Authorization";

    private static final String SWAGGER_UI_HEADER_NAME = "bearerAuth";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 0. Origin 白名单二次校验（CSRF 兜底）：仅对带 Origin 头的浏览器请求生效。
        //    微信小程序 wx.request 不发送 Origin，放行；恶意前端即使拿到 token 也无法跨白名单源调用。
        if (!isOriginAllowed(request)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    Result.forbidden("Origin 不在受信任白名单内"));
            return;
        }

        // 1. 从请求头获取 Token
        String authHeader = request.getHeader(HEADER_NAME);
        if (!StringUtils.hasText(authHeader)) {
            authHeader = request.getHeader(SWAGGER_UI_HEADER_NAME);
        }

        String token = extractToken(authHeader);

        if (StringUtils.hasText(token)) {
            // 注销黑名单校验：已注销账号的 token 立即失效（task-12.8）
            if (tokenBlacklist.isRevoked(token)) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        Result.unauthorized("账号已注销，请重新登录"));
                return;
            }
            // 2. 校验并解析 Token（单次解析，避免重复验签）
            Claims claims = jwtUtil.parseAndValidate(token);
            if (claims != null) {
                // 3. 解析用户信息（复用本次解析结果；role claim 已随 user.role 列退役移除）
                Long userId = claims.get("userId", Long.class);

                // 用户维度失效校验：管理员禁用/删除账号后，该用户此前签发的所有 token 立即失效
                // （管理端拿不到对方 token，只能按 userId 拉黑，故此处补一次判定）
                // 消息涵盖禁用与注销两种来源：本人注销时也会按 userId 兜底拉黑其余设备的旧 token（AuthService.deleteAccount）
                if (userId != null && tokenBlacklist.isUserRevoked(userId)) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                            Result.unauthorized("账号已被禁用或注销，请重新登录"));
                    return;
                }

                if (userId != null) {
                    // 4. 构建认证信息：固定学生态 authorities（JWT 不再携带 role；
                    //    学生接口鉴权实际依赖 @RequireVerified + userId，不依赖角色，
                    //    此处固定授予 ROLE_STUDENT 以兼容既有 @PreAuthorize("hasRole('STUDENT')")）
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId,          // principal：用户ID
                                    null,            // credentials：密码（不需要）
                                    List.of(new SimpleGrantedAuthority("ROLE_STUDENT")) // authorities：固定学生态
                            );
                    // 将用户ID存入 details，方便 Controller 获取
                    authentication.setDetails(userId);

                    // 5. 设置到 SecurityContext（后续请求可直接获取）
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
            // Token 无效则不清空 SecurityContext，相当于未登录
        }

        // 6. 放行（无论是否登录都放行，权限控制由 @PreAuthorize 负责）
        filterChain.doFilter(request, response);
    }

    private String extractToken(String authHeader) {
        if (!StringUtils.hasText(authHeader)) {
            return null;
        }
        String token = authHeader.trim();
        while (token.regionMatches(true, 0, TOKEN_PREFIX, 0, TOKEN_PREFIX.length())) {
            token = token.substring(TOKEN_PREFIX.length()).trim();
        }
        return token;
    }

    /**
     * 校验请求 Origin 是否在白名单内（仅约束带 Origin 头的浏览器请求）。
     * <p>
     * - 未配置白名单（allowedOrigins 为空）：仅放行不带 Origin 的请求（小程序原生请求），拒绝一切浏览器跨域调用。
     * - 配置了白名单：带 Origin 的请求必须精确匹配其中之一，否则拒绝。
     * - 预检 OPTIONS 与不带 Origin 的请求（小程序、服务端间调用）直接放行。
     */
    private boolean isOriginAllowed(HttpServletRequest request) {
        // 小程序 wx.request / 服务端调用无 Origin 头，放行
        String origin = request.getHeader("Origin");
        if (!StringUtils.hasText(origin)) {
            return true;
        }
        // 判据与 CorsConfig 同源：空白名单 ⇒ trustedOrigins() 为空 ⇒ 拒绝任何带 Origin 的请求（fail-closed）
        return corsProperties.trustedOrigins().contains(origin);
    }

    /**
     * 统一的错误响应写出（与 {@code AdminTokenFilter} / {@code SecurityConfig} 同口径）。
     * <p>
     * 不再手写 JSON 字符串：本类曾重复三份同样结构的响应块，契约字段（code / message / data）
     * 一改就要改三处，漏一处即与全局响应壳不一致。
     */
    private static void writeError(HttpServletResponse response, int httpStatus, Result<?> body) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }
}
