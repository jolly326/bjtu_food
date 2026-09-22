package com.bjtufood.common.aspect;

import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.common.annotation.RequireVerified;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.AuthStateUtil;
import com.bjtufood.common.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * {@link RequireVerified} 切面（spec §5.y）。
 * <p>
 * 在请求时按 user.bind_email 实时判定（认证态 = 该列非空；判据唯一真源 {@link AuthStateUtil}；
 * 认证态不进 JWT），未认证抛 403 引导先完成学号邮箱认证。
 * 置于 controller 层切面，保证所有写操作统一鉴权，不重复编码。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RequireVerifiedAspect {

    private final UserMapper userMapper;

    @Before("@annotation(com.bjtufood.common.annotation.RequireVerified)")
    public void checkVerified() {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "请先登录");
        }
        // JWT 载荷不含 status，禁用/注销账号的存量 token 在有效期内仍可被携带，
        // 这里按 user.status 实时判定，非 active 一律拒绝 UGC 写操作。
        if (!"active".equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }
        if (!AuthStateUtil.isVerified(user.getBindEmail())) {
            // 使用细分的业务码 4031 标识「未认证邮箱」，与普通权限拒绝（code=403）区分，
            // 便于前端对「需先认证」与「无权限」给出不同引导（避免越权错误被误导向邮箱认证）。
            throw new BusinessException(4031, "请先完成学号邮箱认证");
        }
    }
}
