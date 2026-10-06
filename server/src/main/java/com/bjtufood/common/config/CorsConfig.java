package com.bjtufood.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域配置（CORS）
 * <p>
 * 功能：允许受信任的前端（微信小程序、H5、管理后台）跨域访问后端 API。
 * <p>
 * 安全约束：
 * 1. **禁用** {@code addAllowedOriginPattern("*")} + allowCredentials（避免任意源携带凭证）。
 * 2. 允许源取自 {@link CorsProperties}，<b>与 {@code JwtAuthFilter} 的 Origin 二次校验共用同一份
 *    配置与同一段解析</b>——两处各自绑定并对「未配置白名单」给出相反处理时（放行 {@code Origin: null}
 *    vs 拒绝一切带 Origin 的请求），就只剩「安全链路先执行」这层巧合兜底，顺序/配置一变即失效。
 * 3. 白名单为空 ⇒ <b>不注册任何允许源</b>：带 Origin 的浏览器请求一律被拒（预检 403、实际请求无 CORS 头），
 *    与 {@code JwtAuthFilter} 的 fail-closed 口径一致；不带 Origin 的请求（{@code wx.request}、
 *    服务端间调用）本就不属 CORS 范畴，不受影响。
 * 4. 本项目鉴权用 Bearer Token（请求头）而非 Cookie，故关闭 allowCredentials。
 * 5. 真正的越权防护仍由 {@code JwtAuthFilter} + {@code @PreAuthorize} 在后端完成，CORS 只是浏览器侧边界。
 */
@Configuration
public class CorsConfig {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 受信任源白名单（仅对带 Origin 头的浏览器请求生效）；空清单 = 全部拒绝（fail-closed）
        config.setAllowedOrigins(corsProperties.trustedOrigins());

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

