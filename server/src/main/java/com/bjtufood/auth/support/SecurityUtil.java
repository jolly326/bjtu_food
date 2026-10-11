package com.bjtufood.auth.support;

import com.bjtufood.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类
 * <p>
 * 封装从 Spring Security SecurityContext 获取当前用户信息的逻辑。
 * 所有需要当前用户 ID 的接口统一通过此工具类获取。
 */
public class SecurityUtil {

    /**
     * 获取当前登录用户的 ID
     * <p>
     * 从 SecurityContext 的 Authentication.principal 中提取 userId。
     * 如果用户未登录则抛 UnauthorizedException。
     *
     * @return 当前用户 ID
     * @throws UnauthorizedException 未登录或登录信息无效
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("请先登录");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        if (principal instanceof String text && text.matches("\\d+")) {
            return Long.valueOf(text);
        }
        throw new UnauthorizedException("登录信息无效");
    }

    /**
     * 获取当前登录用户的 ID（未登录返回 null）
     * <p>
     * 适用于公开接口中可选地附带用户信息。
     *
     * @return 当前用户 ID，未登录返回 null
     */
    public static Long getCurrentUserIdOrNull() {
        try {
            return getCurrentUserId();
        } catch (UnauthorizedException e) {
            return null;
        }
    }

    /**
     * 获取当前登录的**管理员账号 ID**（{@code /admin/**} 处置动作的「处置人」来源）。
     * <p>
     * 管理端鉴权链路由 {@link com.bjtufood.auth.config.AdminAuthFilter} 唯一把关，
     * 验签与账号状态回查通过后把<b>账号 ID</b>写入 {@code Authentication.principal}；
     * 本方法即从同一处取回，故「处置人」恒为库里存在的启用账号，不接受请求体自述。
     *
     * @return 当前管理员账号 ID
     * @throws UnauthorizedException 上下文缺失或非管理端凭证
     */
    public static Long getCurrentAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Long accountId)) {
            throw new UnauthorizedException("管理端登录已失效，请重新登录");
        }
        return accountId;
    }
}
