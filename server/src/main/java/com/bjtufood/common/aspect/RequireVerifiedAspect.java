package com.bjtufood.common.aspect;

import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.common.annotation.RequireVerified;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * {@link RequireVerified} 切面（spec §5.y）。
 * <p>
 * 在请求时按 user.verified 实时判定（verified 不进 JWT），未认证抛 403 引导先完成学号邮箱认证。
 * 置于 controller 层切面，保证所有社区写操作统一鉴权，不重复编码。
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
        if (!Integer.valueOf(1).equals(user.getVerified())) {
            // 使用细分的业务码 4031 标识「未认证邮箱」，与普通权限拒绝（code=403）区分，
            // 便于前端对「需先认证」与「无权限」给出不同引导（避免越权错误被误导向邮箱认证）。
            throw new BusinessException(4031, "请先完成学号邮箱认证");
        }
    }
}
