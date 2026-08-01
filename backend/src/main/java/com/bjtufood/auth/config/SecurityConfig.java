package com.bjtufood.auth.config;

import com.bjtufood.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 安全配置
 * <p>
 * 配置要点：
 * 1. 关闭 CSRF（接口由 JWT 保护，无需 CSRF Token）
 * 2. 无状态会话（不创建 Session，每次请求通过 JWT 验证）
 * 3. 注册 JwtAuthFilter（在 UsernamePasswordAuthenticationFilter 之前执行）
 * 4. 配置公开接口白名单（无需登录即可访问）
 * 5. 启用 @PreAuthorize 注解（方法级别权限控制）
 * <p>
 * 公开接口白名单（无需登录）：
 * - GET /auth/login, POST /auth/register（登录注册）
 * - GET /canteens, GET /stalls（食堂档口查询）
 * - GET /dishes, GET /dishes/hot, GET /dishes/{id}（菜品浏览）
 * - GET /dishes/{dishId}/reviews（评价列表）
 * - GET /lists/share/{token}（分享查看）
 * - Swagger UI (SpringDoc) 相关路径
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 启用 @PreAuthorize 注解
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 任意方法放行的公开接口（鉴权/文档类，无敏感写操作）
     */
    private static final String[] PUBLIC_ANY_METHOD = {
            "/auth/login",
            "/auth/register",
            "/auth/email-code",
            "/auth/password/reset",
            // SpringDoc Swagger UI 文档
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**"
    };

    /**
     * 仅 GET 放行的公开浏览接口（覆盖全部 dish/canteen/stall/review 只读路径，
     * 使用 method-scoped 匹配，避免误放行 POST /dishes、PUT /dishes/{id}、POST /reviews 等写操作）。
     */
    private static final String[] PUBLIC_GET_PREFIXES = {
            "/dishes/**",
            "/canteens/**",
            "/stalls/**",
            "/reviews",
            "/activities/**",
            "/lists/share/**",
            "/images/**",
            // 二期新增：社区动态列表/详情/评论浏览公开（POST/PUT/DELETE 写操作仍须登录）
            "/moments/**",
            "/broadcasts",
            "/categories"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. 关闭 CSRF（JWT 无需 CSRF 保护）
                .csrf(csrf -> csrf.disable())

                // 2. 无状态会话
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. 请求权限配置
                .authorizeHttpRequests(auth -> auth
                        // 任意方法放行的公开接口（鉴权/文档）
                        .requestMatchers(PUBLIC_ANY_METHOD).permitAll()
                        // 仅 GET 放行的公开浏览接口（游客免登录浏览全部公开内容）
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PREFIXES).permitAll()
                        // 管理端接口需要管理员角色
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 其他接口需要登录
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, Result.unauthorized("请先登录或重新登录")))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN, Result.forbidden("无权限访问该接口")))
                )

                // 4. 注册 JWT 过滤器（在 UsernamePasswordAuthenticationFilter 之前）
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码加密器
     * <p>
     * 使用 BCrypt 算法加密密码。
     * BCrypt 每次加密结果不同（内置 salt），安全性高。
     *
     * @return PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeJson(HttpServletResponse response, int httpStatus, Result<?> result) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(result));
    }
}
