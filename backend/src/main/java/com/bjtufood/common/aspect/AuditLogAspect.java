package com.bjtufood.common.aspect;

import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.common.annotation.AuditLog;
import com.bjtufood.common.entity.OperationLog;
import com.bjtufood.common.mapper.OperationLogMapper;
import com.bjtufood.common.utils.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 操作日志切面
 * <p>
 * 仅拦截标注 {@link AuditLog} 的 admin 写方法，写 operation_log。
 * 不拦截查询类（GET），与 §5 D7 一致。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final OperationLogMapper operationLogMapper;
    private final UserMapper userMapper;

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer DISCOVERER = new DefaultParameterNameDiscoverer();

    @Around("@annotation(com.bjtufood.common.annotation.AuditLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        AuditLog annotation = method.getAnnotation(AuditLog.class);

        // targetId 的 SpEL 解析（前置解析，避免方法抛异常后无法取值）
        Long targetId = resolveTargetId(annotation.targetId(), method, joinPoint.getArgs());

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            throw t;
        }

        try {
            writeLog(annotation, targetId);
        } catch (Exception e) {
            // 操作日志写入失败不应影响主业务
            log.warn("操作日志写入失败: action={}, targetType={}", annotation.action(), annotation.targetType(), e);
        }
        return result;
    }

    private void writeLog(AuditLog annotation, Long targetId) {
        Long adminId = getCurrentAdminId();
        String ip = getCurrentIp();
        OperationLog log = new OperationLog();
        log.setAdminId(adminId == null ? 0L : adminId);
        log.setAction(annotation.action());
        log.setTargetType(annotation.targetType());
        log.setTargetId(targetId);
        log.setIp(ip);
        operationLogMapper.insert(log);
    }

    private Long getCurrentAdminId() {
        try {
            return SecurityUtil.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private String getCurrentIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveTargetId(String spel, Method method, Object[] args) {
        if (spel == null || spel.isBlank()) {
            return null;
        }
        try {
            String[] paramNames = DISCOVERER.getParameterNames(method);
            if (paramNames == null) {
                return null;
            }
            EvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
            Expression expr = PARSER.parseExpression(spel);
            Object value = expr.getValue(context);
            if (value == null) return null;
            if (value instanceof Number num) return num.longValue();
            if (value instanceof String s && s.matches("\\d+")) return Long.valueOf(s);
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
