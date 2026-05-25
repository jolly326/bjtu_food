package com.bjtufood.auth.config;

import com.bjtufood.common.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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

    /** 请求头中 Token 的前缀 */
    private static final String TOKEN_PREFIX = "Bearer ";

    /** 请求头名称 */
    private static final String HEADER_NAME = "Authorization";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. 从请求头获取 Token
        String authHeader = request.getHeader(HEADER_NAME);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(TOKEN_PREFIX)) {
            String token = authHeader.substring(TOKEN_PREFIX.length()).trim();

            // 2. 校验 Token
            if (jwtUtil.validateToken(token)) {
                // 3. 解析用户信息
                Long userId = jwtUtil.getUserIdFromToken(token);
                String role = jwtUtil.getRoleFromToken(token);

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
}
