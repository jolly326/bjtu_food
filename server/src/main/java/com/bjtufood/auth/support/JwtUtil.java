package com.bjtufood.auth.support;

import com.bjtufood.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 * <p>
 * 负责 JWT Token 的生成、校验和解析。
 * Token 载荷中存储 userId、username，不存储敏感信息（暂不承载 role）。
 * <p>
 * 流程说明：
 * 1. 登录成功 → createToken() 生成 JWT → 返回给前端
 * 2. 前端每次请求在 Header 中携带 Authorization: Bearer <token>
 * 3. JwtAuthFilter 调用 {@link #parseAndValidate(String)} <b>一次性</b>校验并解析（通过则放行）
 */
@Component
@Slf4j
public class JwtUtil {

    /** JWT 配置（类型化绑定，单一真源） */
    private final JwtProperties jwtProperties;

    /**
     * 缓存的 HMAC 签名密钥（启动时构建一次，全程复用）。
     * <p>
     * <b>不</b>在 {@code validateToken}/{@code getUserIdFromToken}/{@code getUsernameFromToken}
     * 各自 {@code Keys.hmacShaKeyFor} 重建：那会让同一请求重复派生 Key 并多次完整 HMAC 验签
     * （3 个方法各一次），是纯固定开销。
     */
    private volatile SecretKey cachedKey;

    /** 开发期默认弱密钥（仅用于本地调试，生产必须覆盖） */
    private static final String DEV_DEFAULT_SECRET = "BjtuFoodDevSecretKey2024ChangeMe";

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 启动期 fail-fast（BE-11）：密钥缺失/过短/仍是仓库内置默认弱密钥时阻断启动，
     * 防止误用默认密钥导致任意 userId 的 Token 可被伪造。
     * <p>
     * 口径：
     * <ul>
     *   <li>缺失或长度 &lt; 32 字节：HMAC-SHA 算法的硬要求（{@code Keys.hmacShaKeyFor} 会直接抛
     *       WeakKeyException），<b>所有 profile 一律拒绝启动</b>；</li>
     *   <li>等于仓库内置默认密钥：<b>所有 profile 一律拒绝启动</b>（与 README「禁止默认值」口径字面一致），
     *       dev 同样不放行——本地开发必须通过环境变量 JWT_SECRET 注入自己的密钥。</li>
     * </ul>
     * 配置层另有一道闸门：application-prod.yml 将 {@code jwt.secret} 覆盖为无默认值的
     * {@code ${JWT_SECRET}}，prod 漏注入时占位符解析失败、启动直接终止。
     */
    @PostConstruct
    public void validateSecretOnStartup() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT 签名密钥强度不足：请通过环境变量 JWT_SECRET 注入 >=32 字节的强随机密钥，" +
                            "禁止使用默认/弱密钥启动。"
            );
        }
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "检测到仓库内置默认 JWT 密钥：所有环境（含 dev）均禁止使用默认密钥启动，" +
                            "请通过环境变量 JWT_SECRET 注入 >=32 字节的强随机密钥。"
            );
        }
        // 启动时预构建并缓存签名密钥，供后续所有签发/验签复用
        this.cachedKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取缓存的签名密钥（懒加载兜底，正常由 {@link #validateSecretOnStartup} 预热）。
     */
    private SecretKey getKey() {
        SecretKey key = cachedKey;
        if (key == null) {
            synchronized (this) {
                key = cachedKey;
                if (key == null) {
                    key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
                    cachedKey = key;
                }
            }
        }
        return key;
    }

    /**
     * 创建 JWT Token
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @return 签发的 JWT 字符串（如：eyJhbGciOiJIUzI1NiJ9.xxx）
     */
    public String createToken(Long userId, String username) {
        return createToken(userId, username, jwtProperties.getExpiration());
    }

    /**
     * 创建 JWT Token（指定过期时长，毫秒）
     * <p>
     * 用于签发与全局策略不同的短期 Token（如管理后台 12 小时），
     * 由业务侧自行持有过期策略，避免全局统一时长一刀切。
     *
     * @param userId          用户 ID
     * @param username        用户名
     * @param expirationMillis 过期时长（毫秒）
     * @return 签发的 JWT 字符串
     */
    public String createToken(Long userId, String username, long expirationMillis) {
        // 设置载荷（Payload）。注：role 不下发——
        // 学生态 authorities 由 JwtAuthFilter 固定授予（学生接口鉴权依赖 @RequireVerified + userId，不依赖角色）
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);

        // 生成签名密钥（复用缓存 Key）
        SecretKey key = getKey();

        return Jwts.builder()
                .claims(claims)                          // 设置自定义载荷
                .issuedAt(new Date())                    // 签发时间
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))  // 过期时间
                .signWith(key)                           // 签名
                .compact();
    }

    /**
     * <b>本类唯一的解析入口</b>：一次性校验并解析 Token。
     * <p>
     * 供 {@code JwtAuthFilter} 在一次请求中只解析一次 —— 若同时暴露
     * {@code parseToken} / {@code validateToken} / {@code getUserIdFromToken} 三种「同一件事」的入口，
     * 调用者极易在同一请求里重复解析（每请求 3 次验签）。
     *
     * @param token JWT 字符串
     * @return 有效则返回 Claims（含 userId / username）；无效 / 过期 / 格式错误 / 签名不符一律返回 null
     */
    public Claims parseAndValidate(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            // Token 过期、签名错误、格式错误均返回 null
            return null;
        }
    }
}
