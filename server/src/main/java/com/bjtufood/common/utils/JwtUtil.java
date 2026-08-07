package com.bjtufood.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
     * 创建 JWT Token
     *
     * @param userId   用户 ID
     * @param role     用户角色
     * @param username 用户名
     * @return 签发的 JWT 字符串（如：eyJhbGciOiJIUzI1NiJ9.xxx）
     */
    public String createToken(Long userId, String role, String username) {
        // 设置载荷（Payload）
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("username", username);

        // 生成签名密钥
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .claims(claims)                          // 设置自定义载荷
                .issuedAt(new Date())                    // 签发时间
                .expiration(new Date(System.currentTimeMillis() + expiration))  // 过期时间
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
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            // Token 过期、签名错误、格式错误均返回 null
            return null;
        }
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
