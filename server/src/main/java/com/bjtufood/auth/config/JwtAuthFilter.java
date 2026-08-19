package com.bjtufood.auth.config;

import com.bjtufood.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
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

    /** 受信任的前端源白名单（来自 cors.allowed-origins，逗号分隔）；空表示不约束浏览器源 */
    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

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
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"code\":403,\"message\":\"Origin 不在受信任白名单内\",\"data\":null}");
            } catch (Exception ignored) {
            }
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
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                try {
                    response.getWriter().write("{\"code\":401,\"message\":\"账号已注销，请重新登录\",\"data\":null}");
                } catch (Exception ignored) {
                }
                return;
            }
            // 2. 校验并解析 Token（单次解析，避免重复验签）
            Claims claims = jwtUtil.parseAndValidate(token);
            if (claims != null) {
                // 3. 解析用户信息（复用本次解析结果）
                Long userId = claims.get("userId", Long.class);
                String role = claims.get("role", String.class);

                // 用户维度失效校验：管理员禁用/删除账号后，该用户此前签发的所有 token 立即失效
                // （管理端拿不到对方 token，只能按 userId 拉黑，故此处补一次判定）
                if (userId != null && tokenBlacklist.isUserRevoked(userId)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    try {
                        response.getWriter().write("{\"code\":401,\"message\":\"账号已被禁用，请联系管理员\",\"data\":null}");
                    } catch (Exception ignored) {
                    }
                    return;
                }

                if (userId != null && role != null) {
                    // 4. 构建认证信息（角色加 ROLE_ 前缀）
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId,          // principal：用户ID
                                    null,            // credentials：密码（不需要）
                                    List.of(authority) // authorities：角色
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
        // 未配置白名单：拒绝任何带 Origin 的浏览器请求
        if (!StringUtils.hasText(allowedOrigins)) {
            return false;
        }
        List<String> trusted = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        return trusted.contains(origin);
    }
}
