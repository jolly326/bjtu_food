package com.bjtufood.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
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
 * Token 载荷中存储 userId、role、username，不存储敏感信息。
 * <p>
 * 流程说明：
 * 1. 登录成功 → createToken() 生成 JWT → 返回给前端
 * 2. 前端每次请求在 Header 中携带 Authorization: Bearer <token>
 * 3. JwtAuthFilter 调用 validateToken() 校验 → 通过则放行
 */
@Component
public class JwtUtil {

    /** JWT 签名密钥（从配置读取） */
    @Value("${jwt.secret}")
    private String secret;

    /** 过期时间（毫秒） */
    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * 缓存的 HMAC 签名密钥。
     * <p>
     * 原实现每次 {@code validateToken}/{@code getUserIdFromToken}/{@code getRoleFromToken}
     * 都各自 {@code Keys.hmacShaKeyFor} 重建 Key 并完整验签一次（每请求 3 次 HMAC 验签）。
     * 改为启动时构建一次并复用，避免每请求重复重建与多次验签的固定开销。
     */
    private volatile SecretKey cachedKey;

    /** 开发期默认弱密钥（仅用于本地调试，生产必须覆盖） */
    private static final String DEV_DEFAULT_SECRET = "BjtuFoodDevSecretKey2024ChangeMe";

    /**
     * 启动期 fail-fast：若仍使用仓库内置的默认弱密钥，直接阻断启动，
     * 防止误用默认密钥导致任意用户 Token 可被伪造。
     */
    @PostConstruct
    public void validateSecretOnStartup() {
        if (secret == null || secret.equals(DEV_DEFAULT_SECRET) || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT 签名密钥强度不足：请通过环境变量 JWT_SECRET 注入 >=32 字节的强随机密钥，" +
                            "禁止使用默认/弱密钥启动生产环境。"
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
                    key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
     * @param role     用户角色
     * @param username 用户名
     * @return 签发的 JWT 字符串（如：eyJhbGciOiJIUzI1NiJ9.xxx）
     */
    public String createToken(Long userId, String role, String username) {
        return createToken(userId, role, username, expiration);
    }

    /**
     * 创建 JWT Token（指定过期时长，毫秒）
     * <p>
     * 用于签发与全局策略不同的短期 Token（如管理后台 12 小时），
     * 由业务侧自行持有过期策略，避免全局统一时长一刀切。
     *
     * @param userId          用户 ID
     * @param role            用户角色
     * @param username        用户名
     * @param expirationMillis 过期时长（毫秒）
     * @return 签发的 JWT 字符串
     */
    public String createToken(Long userId, String role, String username, long expirationMillis) {
        // 设置载荷（Payload）
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
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
     * 验证并解析 Token
     *
     * @param token JWT 字符串
     * @return 解析后的 Claims（包含 userId、role、username），
     *         如果 token 无效/过期返回 null
     */
    public Claims parseToken(String token) {
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

    /**
     * 一次性校验并解析 Token，返回 Claims。
     * <p>
     * 供 {@code JwtAuthFilter} 在一次请求中只解析一次（原实现在 filter 内分别调用
     * {@link #validateToken}、{@link #getUserIdFromToken}、{@link #getRoleFromToken}，
     * 触发 3 次独立验签）。调用方应先判非空，再读取 userId/role，避免重复解析。
     *
     * @param token JWT 字符串
     * @return 有效则返回 Claims，否则返回 null
     */
    public Claims parseAndValidate(String token) {
        return parseToken(token);
    }

    /**
     * 判断 Token 是否有效
     *
     * @param token JWT 字符串
     * @return true=有效, false=无效或已过期
     */
    public boolean validateToken(String token) {
        return parseToken(token) != null;
    }

    /**
     * 从 Token 中获取用户 ID
     *
     * @param token JWT 字符串
     * @return 用户 ID，无效 token 返回 null
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("userId", Long.class) : null;
    }

    /**
     * 从 Token 中获取用户角色
     *
     * @param token JWT 字符串
     * @return 角色名，无效 token 返回 null
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("role", String.class) : null;
    }
}
