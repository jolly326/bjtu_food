package com.bjtufood.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 要求当前用户已完成学号邮箱认证（判据 = {@code user.bind_email} 非空，spec §5.y）。
 * <p>
 * 用于需认证的 UGC 写操作（写评价、点赞等）。
 * <p>
 * 注：学生端菜品写接口已于 2026-09-13 全部下线，菜品由管理员录入，故本注解现仅覆盖评价类 UGC。
 * 认证态不进 JWT，由 {@link com.bjtufood.common.aspect.RequireVerifiedAspect}
 * 在请求时按 user.bind_email 非空实时判定（判据唯一真源 {@code AuthStateUtil}）；未认证返回 4031。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireVerified {
}
