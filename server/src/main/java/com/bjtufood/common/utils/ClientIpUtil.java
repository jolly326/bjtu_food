package com.bjtufood.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 客户端 IP 解析工具
 * <p>
 * 统一 IP 取值口径（与 RequestLoggingFilter / AuditLogAspect 的既有逻辑一致）：
 * X-Forwarded-For 首段（云托管/反向代理场景，取真实客户端）→
 * X-Real-IP（单层代理）→ {@code getRemoteAddr()} 兜底（直连）。
 * <p>
 * 已知取舍：X-Forwarded-For 可被客户端伪造，依赖接入层（云托管/网关）重写；
 * 与全站既有取值口径保持一致，仅用于限频/日志等防损场景，不用于安全身份判定。
 */
public final class ClientIpUtil {

    private ClientIpUtil() {
    }

    /**
     * 从请求解析客户端 IP：X-Forwarded-For 首段 → X-Real-IP → getRemoteAddr 兜底。
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * 解析当前线程绑定的客户端 IP（无 Web 上下文时返回 null，不抛异常）。
     * 适用于 Controller / Service 调用栈内快速取 IP。
     */
    public static String resolveCurrent() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                return resolve(servletAttrs.getRequest());
            }
        } catch (Exception ignored) {
            // 非 Web 上下文（定时任务/异步线程）：无请求可绑定，返回 null
        }
        return null;
    }
}
