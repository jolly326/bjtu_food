package com.bjtufood.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * 跨域配置（CORS）
 * <p>
 * 功能：允许受信任的前端（微信小程序、H5、管理后台）跨域访问后端 API。
 * <p>
 * 安全约束：
 * 1. 不再使用 {@code addAllowedOriginPattern("*")} + allowCredentials，避免任意源携带凭证。
 * 2. 允许源从环境变量 {@code CORS_ALLOWED_ORIGINS} 注入（逗号分隔），仅放行白名单内的浏览器源。
 * 3. 本项目鉴权使用 Bearer Token（请求头）而非 Cookie，故关闭 allowCredentials。
 * 4. 微信小程序 {@code wx.request} 不发送 Origin 头，浏览器才带 Origin；白名单仅约束带 Origin 的请求，
 *    真正的越权防护仍由 JwtAuthFilter + @PreAuthorize 在后端完成。
 */
@Configuration
public class CorsConfig {

    /** 受信任的前端源（逗号分隔）；空值表示仅放行不带 Origin 的请求（如小程序原生请求） */
    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 受信任源白名单（仅对带 Origin 头的浏览器请求生效）
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            // 无白名单配置：仅放行不带 Origin 的请求（小程序原生请求），拒绝未知浏览器源
            config.addAllowedOriginPattern("null");
        } else {
            config.setAllowedOrigins(origins);
        }

        // 允许的 HTTP 方法
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");

        // 允许的请求头
        config.addAllowedHeader("*");

        // 本项目用 Bearer Token 鉴权，无需 Cookie 凭证；关闭以避免 CSRF 类风险
        config.setAllowCredentials(false);

        // 预检请求缓存时间（秒），减少 OPTIONS 请求
        config.setMaxAge(3600L);

        // 注册到所有路径
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
