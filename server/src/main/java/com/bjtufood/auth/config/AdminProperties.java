package com.bjtufood.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 管理端配置（类型化绑定，单一真源）。
 * <p>
 * <b>归属</b>：随 {@code auth} 域走。
 * <p>
 * <b>两部分</b>：
 * <ul>
 *   <li>{@link #getToken()} —— 过渡期的静态口令（环境变量 {@code ADMIN_TOKEN}）。
 *       🔴 <b>已废弃</b>（TD-20 落地后删除）：口令须在前后端各存一份而前端那份会被 Vite
 *       打进产物，bundle 一旦离开本机即永久失守且无法吊销。</li>
 *   <li>{@link #getJwt()} —— 账密登录后的 JWT 签发配置（TD-18），<b>独立于学生端</b>。</li>
 * </ul>
 * <p>
 * {@link #isConfigured()} 是「未配置即 fail-closed 拒绝全部 /admin」的显式判据（可复用、可测试），
 * 不再内联在过滤器方法里。
 */
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    /** 管理端口令（环境变量 {@code ADMIN_TOKEN} 注入；未配置时 fail-closed 拒绝全部 /admin 请求） */
    private String token = "";

    /** 管理端 JWT 配置（TD-18 · P0-4）：账密登录后签发的凭证，**独立于学生端 {@code spring.jwt.secret}** */
    private Jwt jwt = new Jwt();

    /**
     * 管理端 JWT 子配置。
     * <p>
     * 🔴 <b>为什么与学生端 JWT 完全分离</b>：共用 secret 会使两套凭证在密码学上互通 ——
     * 学生只要持有自己的合法 token，就可能伪造出带管理端身份的 token。两套密钥独立后，
     * 任一侧泄露都攻不到另一侧。
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

    /** 口令是否已配置（未配置时管理端必须 fail-closed，避免公网裸奔） */
    public boolean isConfigured() {
        return StringUtils.hasText(token);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }
}
