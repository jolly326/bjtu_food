package com.bjtufood.auth.config;

import com.bjtufood.common.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注销 Token 黑名单（task-12.8）
 * <p>
 * 账号注销后将当前 token 加入黑名单，使已签发的 JWT 立即失效（spec 要求「注销即失效当前 token」）。
 * 内存存储，按 token 过期时间自动清理，重启后清空（可接受：注销后用户需重新登录）。
 */
@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private final JwtUtil jwtUtil;

    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    /**
     * 将 token 加入黑名单，过期时间取该 token 自身的剩余有效期。
     */
    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        long exp = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000; // 默认 7 天兜底
        try {
            var claims = jwtUtil.parseToken(token);
            if (claims != null && claims.getExpiration() != null) {
                exp = claims.getExpiration().getTime();
            }
        } catch (Exception ignored) {
            // 解析失败则用兜底过期时间
        }
        blacklist.put(token, exp);
    }

    public boolean isRevoked(String token) {
        return token != null && blacklist.containsKey(token);
    }

    /** 每分钟清理已过期的黑名单项 */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        blacklist.entrySet().removeIf(e -> e.getValue() <= now);
    }
}
