package com.bjtufood.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 要求当前用户已完成学号邮箱认证（user.verified=1，spec §5.y）。
 * <p>
 * 用于社区写操作（发布菜品、提交档口/食堂、写评价、评论、点赞、动态等）。
 * verified 不进 JWT，由 {@link com.bjtufood.common.aspect.RequireVerifiedAspect}
 * 在请求时按 user.verified 实时判定；未认证返回 403。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireVerified {
}
