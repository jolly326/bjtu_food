package com.bjtufood.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理端配置（类型化绑定，单一真源）。
 * <p>
 * <b>归属</b>：随 {@code auth} 域走。
 * <p>
 * 承载 {@link #getJwt()} —— 管理员账密登录后签发的 JWT 配置，**独立于学生端
 * {@code spring.jwt.secret}**：两套密钥独立后，任一侧泄露都攻不到另一侧。
 */
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    /** 管理端 JWT 配置：账密登录后签发的凭证 */
    private Jwt jwt = new Jwt();

    /**
     * 管理端 JWT 子配置。
     * <p>
     * 🔴 <b>为什么与学生端 JWT 完全分离</b>：共用 secret 会使两套凭证在密码学上互通 ——
     * 学生只要持有自己的合法 token，就可能伪造出带管理端身份的 token。
     */
    public static class Jwt {
        /** 签名密钥（环境变量 {@code ADMIN_JWT_SECRET}；未配置或过弱 ⇒ 启动即失败 fail-fast） */
        private String secret = "";

        /** token 有效期（秒）；默认 **86400 = 24h**，对应「一人偶尔用，过期后重登成本低」 */
        private long expirationSeconds = 86_400L;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationSeconds() {
            return expirationSeconds;
        }

        public void setExpirationSeconds(long expirationSeconds) {
            this.expirationSeconds = expirationSeconds;
        }
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }
}
