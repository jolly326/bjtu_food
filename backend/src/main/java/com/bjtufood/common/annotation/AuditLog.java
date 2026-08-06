package com.bjtufood.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志埋点注解
 * <p>
 * 标注在 admin 写方法上，由 {@code AuditLogAspect} 拦截，
 * 从 SecurityContext 取 adminId、从 HttpServletRequest 取 ip，入库 operation_log。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 动作标识，如 audit_approve / moment_hide / feedback_handle */
    String action();

    /** 操作对象类型，如 moment / dish / feedback */
    String targetType();

    /**
     * 操作对象ID的 SpEL 表达式（从方法参数取值）。
     * 例如 "#id" 表示取名为 id 的参数；为空则写 null。
     */
    String targetId() default "";
}
