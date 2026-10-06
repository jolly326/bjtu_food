package com.bjtufood.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置（类型化绑定，单一真源）。
 * <p>
 * <b>归属</b>：随 {@code auth} 域走，而非 {@code common}——
 * JWT 签发与校验是认证域私有的能力（配置项含密钥，不属于跨模块通用件）。
 * <p>
 * 类型化绑定，便于统一注入测试（单测可直接 new 出本对象，不再依赖 {@code @TestPropertySource}）。
 * <p>
 * 密钥强度校验仍在 {@code JwtUtil.validateSecretOnStartup}（@PostConstruct fail-fast），
 * 属<b>业务规则</b>（≥32 字节、禁仓库默认值），不宜塞进框架级 {@code @Validated}；
 * 本类只负责承载与绑定。
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** JWT 签名密钥（生产/云端必须由环境变量 {@code JWT_SECRET} 注入强随机密钥） */
    private String secret = "";

    /** Token 过期时间（毫秒） */
    private long expiration = 0L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        this.expiration = expiration;
    }
}
