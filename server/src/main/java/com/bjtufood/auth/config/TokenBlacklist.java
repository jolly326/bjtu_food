package com.bjtufood.auth.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注销 Token 黑名单（task-12.8）
 * <p>
 * 两个维度：
 * <ul>
 *   <li>token 维度：本人注销时把「当前请求 token」拉黑（本人操作，token 可直接拿到）；</li>
 *   <li>userId 维度：管理员禁用/删除他人账号时，管理员无法拿到目标用户的 token，
 *       因此按 userId 记录失效时间点，使该用户所有历史 token 一并失效。</li>
 * </ul>
 * 内存存储，定时清理，重启后清空（可接受：被禁用/注销账号本就无法再登录）。
 */
@Component
public class TokenBlacklist {

    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    /** 按用户维度的失效名单：userId -> 失效项到期时间 */
    private final Map<Long, Long> revokedUsers = new ConcurrentHashMap<>();

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

    /**
     * 将某个用户的全部已签发 token 置为失效（管理员禁用 / 删除账号场景）。
     * <p>
     * 管理端操作的是他人账号，拿不到对方的 token，只能按 userId 维度拉黑。
     * 存活窗口同样为固定 7 天：被禁用/删除的账号无法再登录换取新 token，7 天后旧 token 已无实际持有方。
     */
    public void revokeUser(Long userId) {
        if (userId == null) return;
        if (revokedUsers.size() > MAX_ENTRIES) {
            cleanup();
        }
        revokedUsers.put(userId, System.currentTimeMillis() + REVOKE_TTL_MS);
    }

    /** 该用户是否已被整体拉黑（禁用/删除） */
    public boolean isUserRevoked(Long userId) {
        if (userId == null) return false;
        Long exp = revokedUsers.get(userId);
        return exp != null && exp > System.currentTimeMillis();
    }

    /**
     * 用户重新变为可用（管理员把 disabled 改回 active）时解除拉黑，
     * 避免恢复启用后仍被 7 天窗口拦住。
     */
    public void restoreUser(Long userId) {
        if (userId == null) return;
        revokedUsers.remove(userId);
    }

    /** 每分钟清理已过期的黑名单项（token 维度 + 用户维度） */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        blacklist.entrySet().removeIf(e -> e.getValue() <= now);
        revokedUsers.entrySet().removeIf(e -> e.getValue() <= now);
    }
}
