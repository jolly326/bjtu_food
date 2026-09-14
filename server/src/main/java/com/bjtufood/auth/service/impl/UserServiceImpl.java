package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.dto.UserVO;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.constant.RoleConst;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final ImageUrlUtil imageUrlUtil;
    private final com.bjtufood.auth.config.TokenBlacklist tokenBlacklist;

    @Override
    public IPage<UserVO> listUsers(int page, int pageSize, String role, String status) {
        int[] p = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(StringUtils.hasText(role), User::getRole, role)
                .eq(StringUtils.hasText(status), User::getStatus, status)
                .orderByDesc(User::getCreatedAt);
        return userMapper.selectPage(new Page<>(page, pageSize), wrapper).convert(this::toVO);
    }

    @Override
    public User getByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }

    @Override
    public User getByOpenid(String openid) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
    }

    @Override
    public User getByBindEmail(String bindEmail) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getBindEmail, bindEmail));
    }

    @Override
    public void updateStatus(Long id, String status) {
        // 枚举校验：本接口契约仅允许 active/disabled（对齐 AdminManagerServiceImpl.updateStatus），
        // 非法值（含 deleted）一律 400，避免垃圾值直接落库
        if (!"active".equals(status) && !"disabled".equals(status)) {
            throw new BusinessException("非法的状态：" + status);
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("User not found");
        }
        // 说明：管理端已无登录与角色体系（2026-09-13 定型），用户状态变更不再做「禁止操作自身/越权」判定；
        // 管理端接口整体由 AdminTokenFilter 的口令校验保护。
        user.setStatus(status);
        userMapper.updateById(user);
        // 禁用后该用户已签发的 token 必须立即失效（否则改了状态仍能带旧 token 访问）；
        // 恢复 active 时解除拉黑，使其可正常登录使用。
        // （deleted 状态仅由微信账号合并流程在 AuthServiceImpl 内部写入，不经本接口）
        if ("disabled".equals(status)) {
            tokenBlacklist.revokeUser(id);
        } else {
            tokenBlacklist.restoreUser(id);
        }
    }

    // 垂直越权防护（checkAdminOperation / resolveOperatorRole）已随管理端角色体系一并移除：
    // 后台无登录、无角色、单一使用者，管理端接口由 AdminTokenFilter 口令校验统一保护。

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setAvatar(imageUrlUtil.toAbsoluteUrl(user.getAvatar()));
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setVerified(user.getVerified());
        vo.setBindEmail(user.getBindEmail());
        vo.setGuestShortId(buildGuestShortId(user.getId()));
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    /** 游客短标识：食客 + ID 尾 4 位 */
    private String buildGuestShortId(Long userId) {
        String id = String.valueOf(userId);
        String tail = id.length() > 4 ? id.substring(id.length() - 4) : id;
        return "食客" + tail;
    }
}
