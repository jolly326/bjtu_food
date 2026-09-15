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
 * - POST /auth/wechat-login（微信静默登录）、POST /auth/email-code（发验证码）、POST /auth/verify-email（邮箱认证）
 * - 管理端 /admin/** 不走白名单：由 AdminTokenFilter 校验请求头 X-Admin-Token（环境变量 ADMIN_TOKEN，方案 C 已作废）
 * - GET /canteens, GET /stalls（食堂档口查询）
 * - GET /dishes, GET /dishes/hot-search, GET /dishes/{id}（菜品浏览）
 * - Swagger UI (SpringDoc) 相关路径
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 启用 @PreAuthorize 注解
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    private final AdminTokenFilter adminTokenFilter;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 任意方法放行的公开接口（鉴权/文档类，无敏感写操作）
     */
    // 注意：server.servlet.context-path=/api 时，Spring Security 的 requestMatchers 是否包含
    // context-path 取决于 matcher 实现（AntPath 去前缀 / MvcRequest 含前缀）。为兼容两种行为、
    // 避免白名单因 context-path 不命中导致游客态全 401（含 wechat-login 死循环），每条路径同时
    // 列出「无前缀」与「/api 前缀」两种写法，二者必中其一，且不影响既有公开范围。
    private static final String[] PUBLIC_ANY_METHOD = {
            // 认证类公开接口（微信静默登录、学号邮箱认证、验证码）
            "/auth/wechat-login", "/api/auth/wechat-login",
            "/auth/email-code", "/api/auth/email-code",
            "/auth/verify-email", "/api/auth/verify-email",
            // 反馈提交（PUB：产品决策「反馈不登录也能用」）
            "/feedback", "/api/feedback",
            // SpringDoc Swagger UI 文档
            "/swagger-ui/**", "/api/swagger-ui/**",
            "/v3/api-docs/**", "/api/v3/api-docs/**",
            "/webjars/**", "/api/webjars/**"
    };

    /**
     * 仅 GET 放行的公开浏览接口（覆盖全部 dish/canteen/stall/review 只读路径，
     * 使用 method-scoped 匹配，避免误放行 POST /reviews 等写操作）。
     * <p>
     * 说明：学生端菜品写接口已于 2026-09-13 全部下线，菜品仅由管理员经 /admin/dishes 录入；
     * 本条仅约束 GET 只读浏览，POST /dishes/{id}/view（浏览量上报）与 GET 系列仍保留。
     */
    private static final String[] PUBLIC_GET_PREFIXES = {
            "/dishes/**", "/api/dishes/**",
            "/canteens/**", "/api/canteens/**",
            "/stalls/**", "/api/stalls/**",
            "/reviews", "/api/reviews",
            "/images/**", "/api/images/**",
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
                        // 管理端接口：由 AdminTokenFilter 用环境变量口令 ADMIN_TOKEN 校验（后台无登录体系），
                        // 此处放行交由过滤器把关（未配置口令时过滤器 fail-closed 拒绝）
                        .requestMatchers("/admin/**").permitAll()
                        // 管理端图片上传（web 后台上传菜品图，multipart）：与 /admin/** 同源、同口令把关。
                        // 不在此放行会落到 anyRequest().authenticated() → 后台上传 401（2026-09-14 实测）。
                        // 注意：学生端 /upload/images 不在本行，仍走 JWT。
                        .requestMatchers("/upload/image", "/api/upload/image").permitAll()
                        // 其他接口需要登录
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, Result.unauthorized("请先登录或重新登录")))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN, Result.forbidden("无权限访问该接口")))
                )

                // 4. 注册管理端口令过滤器（先注册即先执行：/admin 走口令，不再走 JWT 角色）
                .addFilterBefore(adminTokenFilter, UsernamePasswordAuthenticationFilter.class)

                // 5. 注册 JWT 过滤器（在 UsernamePasswordAuthenticationFilter 之前）
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码加密器
     * <p>
     * 使用 BCrypt 算法加密密码。
     * BCrypt 每次加密结果不同（内置 salt），安全性高。
     * <p>
     * 2026-09-14 保留说明（BE「删除 DataInitializer」联动评估结论）：
     * 管理端已无登录/密码体系（/admin/** 走 {@code AdminTokenFilter} 的 X-Admin-Token 口令），
     * 原 DataInitializer 写入的 admin/admin123 账号已随该类一并删除；
     * 但本 Bean <b>不能摘除</b>——邮箱验证码仍以 BCrypt 存/验哈希：
     * {@code EmailCodeServiceImpl#passwordEncoder.encode(验证码)} 与
     * {@code AuthServiceImpl#passwordEncoder.matches(输入码, code_hash)} 依赖它。
     * 摘除将直接导致启动期依赖注入失败。
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
