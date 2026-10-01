package com.bjtufood.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 受信任前端源配置（类型化绑定，<b>单一真源</b>）。
 * <p>
 * <b>为什么要有这个类</b>：{@code cors.allowed-origins} 此前被<b>两处各自 {@code @Value} 绑定</b>——
 * {@code CorsConfig}（配置 CorsFilter 的允许源）与 {@code JwtAuthFilter}（Origin 二次校验）。
 * 两处对「未配置白名单」的处理<b>并不一致</b>：前者 {@code addAllowedOriginPattern("null")}
 * （放行 {@code Origin: null}，即 file:// 与 sandboxed iframe 的源），后者拒绝一切带 Origin 的请求。
 * 当前是「安全链路的过滤器先执行」才让拒绝赢下来，属**依赖执行顺序的巧合**；一旦顺序或配置变动，
 * 那个被放行的 null 源就会生效。现收敛为一份配置 + 一个判据，两处不可能再分叉。
 * <p>
 * <b>安全口径</b>：白名单为空 ⇒ {@link #trustedOrigins()} 返回空清单 ⇒ 两处一律**拒绝**带 Origin 的
 * 浏览器请求（fail-closed）；不带 Origin 的请求（微信小程序 {@code wx.request}、服务端间调用）
 * 本就不属于 CORS 范畴，不受影响。
 */
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

    /** 受信任前端源（逗号分隔；由环境变量 {@code CORS_ALLOWED_ORIGINS} 注入，缺省为空 = 不信任任何浏览器源） */
    private String allowedOrigins = "";

    /**
     * 解析后的来源清单：去空白、过滤空项、去重保序。
     * <p>
     * 唯一判据 —— {@code CorsConfig} 与 {@code JwtAuthFilter} 都必须走这里，不得各自 split。
     */
    public List<String> trustedOrigins() {
        if (!StringUtils.hasText(allowedOrigins)) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? "" : allowedOrigins;
    }
}
