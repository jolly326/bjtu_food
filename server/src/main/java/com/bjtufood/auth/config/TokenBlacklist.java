package com.bjtufood.auth.config;

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
public class TokenBlacklist {

    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    /** 黑名单项存活窗口：固定 7 天 */
    private static final long REVOKE_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    /** 内存兜底容量上限，超过时先做一次清理，避免异常场景下无限增长 */
    private static final int MAX_ENTRIES = 100_000;

    /**
     * 将 token 加入黑名单。
     * <p>
     * 存活期使用固定短窗口（7 天）而非 token 自身的 exp：JWT 有效期为产品决策的超长时长（100 年），
     * 若按 token exp 清理会让黑名单项常驻内存造成泄漏。7 天窗口足以覆盖「注销/禁用后旧 token 立即失效」的诉求，
     * 因为被注销/禁用的账号无法再次登录换取新 token，且服务重启后黑名单清空同样要求重新登录。
     */
    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        // 容量兜底：先清理已过期项，避免异常流量下无限增长
        if (blacklist.size() > MAX_ENTRIES) {
            cleanup();
        }
        blacklist.put(token, System.currentTimeMillis() + REVOKE_TTL_MS);
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
