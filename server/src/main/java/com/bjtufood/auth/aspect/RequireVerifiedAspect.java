package com.bjtufood.auth.aspect;

import com.bjtufood.auth.service.UserService;
import com.bjtufood.auth.support.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 认证拦截切面（DEV-13：UGC 写操作前置守卫）
 * <p>
 * 拦截 {@code @RequireVerified} 注解方法，实时查 user.bind_email（认证态唯一判据，
 * 判据真源 {@code AuthStateUtil}）：未登录 → 401(code 401)、邮箱未绑定 → 403(code 4031)。
 * <p>
 * <b>不信任 JWT 快照</b>：本切面在请求时实时查库（selectById 主键 O(1)，UGC 写 QPS 低），
 * 保证解绑邮箱后立即生效，防止「已认证 JWT 长期有效 → 解绑后仍可写 UGC」越权窗口。
 * <p>
 * <b>错误码语义</b>：401 = 未登录（前端 refreshAccessToken 静默重登）、
 * 403 = 已登录但权限不足（前端硬跳登录属既有过度拦截，非本轮范围）。
 * 与 {@code UserServiceImpl.resolveVerifiedUser}（提交评价时同样拒绝未认证用户）
 * 同 code 同语义，保证前端对「未认证」的拦截行为一致。
 * <p>
 * 归属 {@code auth.aspect}：认证态判定属 auth 领域知识 —— common **不得依赖业务包**，
 * 故切面只经 {@link UserService#requireUgcAuthorized(Long)} 判定；
 * 注解 {@code common.annotation.RequireVerified} 留在 common，供各模块标注。
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RequireVerifiedAspect {

    private final UserService userService;

    @Before("@annotation(com.bjtufood.common.annotation.RequireVerified)")
    public void checkVerified() {
        userService.requireUgcAuthorized(SecurityUtil.getCurrentUserId());
    }
}
