package com.bjtufood.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs client requests after Spring Security has resolved the current user.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.endsWith("/favicon.ico")
                || uri.contains("/images/")
                || uri.contains("/webjars/")
                || uri.contains("/swagger-ui/")
                || uri.contains("/v3/api-docs")
                || uri.endsWith("/doc.html");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            log.info("client_request method={} uri={} query={} ip={} userId={} status={} cost={}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    normalize(request.getQueryString()),
                    getClientIp(request),
                    getCurrentUserId(),
                    response.getStatus(),
                    cost);
        }
    }

    private String getClientIp(HttpServletRequest request) {
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

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return String.valueOf(userId);
        }
        if (principal instanceof String principalText && StringUtils.hasText(principalText)
                && !"anonymousUser".equals(principalText)) {
            return principalText;
        }
        return "anonymous";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
