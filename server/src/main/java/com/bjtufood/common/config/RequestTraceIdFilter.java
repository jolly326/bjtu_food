package com.bjtufood.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求 traceId 过滤器（2026-09-28 架构收口 D：可观测性）。
 * <p>
 * 为每个请求分配一个 {@code traceId} 并写入 SLF4J 的 {@link MDC}，使
 * <b>一次请求内跨 controller / service / 跨域调用的全部日志可用同一 traceId 串联</b>；
 * 同时回写到响应头 {@code X-Trace-Id}，便于前端/网关在报障时直接给出该值。
 * <p>
 * <b>优先级</b>：{@link Ordered#HIGHEST_PRECEDENCE + 10}——必须早于
 * {@link RequestLoggingFilter}（其日志含 traceId 字段）与全部业务过滤器，
 * 否则首个过滤器打出的日志将缺失 traceId。
 * <p>
 * <b>入参信任边界</b>：仅当上游传入的 {@code X-Trace-Id} 匹配
 * {@code [A-Za-z0-9_-]{1,64}（非空、长度受限、字符集受限）时才复用，
 * 否则一律重新生成。不可信输入直接拼进日志会造成<b>日志伪造</b>（CRLF 注入伪造日志行），
 * 这是网关透传 traceId 的常见漏洞。
 * <p>
 * <b>收尾</b>：{@code finally} 中 {@link MDC#remove} —— 容器线程复用，若不清除会把
 * 上一个请求的 traceId 泄漏到下一个请求的日志中（无状态体系必须保证这点）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestTraceIdFilter extends OncePerRequestFilter {

    /** 响应/请求头中的 traceId 字段名 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final String MDC_KEY = "traceId";

    /** 合法的外部 traceId 形态：字母数字下划线连字符，1~64 位（防日志伪造与超长注入） */
    private static final java.util.regex.Pattern VALID_TRACE_ID =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_ID_HEADER));
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 必须清理：容器线程复用，否则上一个请求的 traceId 会污染下一个请求的日志
            MDC.remove(MDC_KEY);
        }
    }

    /** 复用可信的上游 traceId，否则生成新的（UUID 去连字符，32 位） */
    private static String resolveTraceId(String incoming) {
        if (incoming != null && VALID_TRACE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
