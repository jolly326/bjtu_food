package com.bjtufood.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 客户端 IP 解析工具
 * <p>
 * 统一 IP 取值口径：
 * X-Forwarded-For **末段**（可信代理追加的那一跳）→
 * X-Real-IP（单层代理）→ {@code getRemoteAddr()} 兜底（直连）。
 * <p>
 * 安全取舍：XFF 左侧各段由客户端自填、不可信，故取末段；本值仅用于限频/日志等防损场景，
 * <b>不用于安全身份判定</b>（身份一律走 JWT）。若接入层改为**覆盖式**写 XFF，本取值依然正确。
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
            // 取**末段**而非首段（2026-09-29 安全修正）：XFF 的左侧各段完全由客户端自填，
            // 只有「可信代理追加在链尾的那一段」才反映代理实际看到的对端地址。
            // 取首段等于把限频 key 交给调用方自选 → 一句 header 即可绕过全站 IP 限频
            // （发码 / 反馈 / 举报 / 纠错 / 浏览计数）。
            String[] hops = forwardedFor.split(",");
            String last = hops[hops.length - 1].trim();
            if (StringUtils.hasText(last)) {
                return last;
            }
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
