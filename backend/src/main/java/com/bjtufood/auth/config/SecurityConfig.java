package com.bjtufood.auth.config;

import com.bjtufood.common.constant.RoleConst;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
 * - Knife4j 相关路径
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 启用 @PreAuthorize 注解
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    /** 公开接口路径（无需登录） */
    private static final String[] PUBLIC_URLS = {
            "/auth/login",
            "/auth/register",
            "/canteens/**",
            "/stalls/**",
            "/dishes",
            "/dishes/hot",
            "/dishes/{id:[0-9]+}",
            "/dishes/{dishId:[0-9]+}/reviews",
            "/lists/share/**",
            // Knife4j / Swagger 文档
            "/doc.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**",
            "/swagger-resources/**"
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
                        // 公开接口
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/images/**").permitAll()  // 上传的静态图片资源
                        // 管理端接口需要管理员角色
                        .requestMatchers("/admin/**").hasAnyRole("CANTEEN_ADMIN", "SYS_ADMIN")
                        // 其他接口需要登录
                        .anyRequest().authenticated()
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
}
