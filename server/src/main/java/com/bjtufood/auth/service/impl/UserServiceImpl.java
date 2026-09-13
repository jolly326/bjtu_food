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
    public User getByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
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
        // 垂直越权防护：禁止操作自身；目标为管理员/超级管理员时，仅 SUPER_ADMIN 可操作
        checkAdminOperation(user);
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

    @Override
    public void updateRole(Long id, String role) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("User not found");
        }
        // 垂直越权防护：禁止操作自身；目标为管理员/超级管理员时，仅 SUPER_ADMIN 可操作
        checkAdminOperation(user);
        if (!RoleConst.STUDENT.equals(role) && !RoleConst.ADMIN.equals(role)) {
            throw new BusinessException("角色只能设置为 student 或 admin");
        }
        user.setRole(role);
        userMapper.updateById(user);
        // 角色变更（含降权）后旧 token 载荷中的 role 已过时，必须整体失效：
        // 按用户维度拉黑，客户端下次请求 401 后重新登录换取携带新角色的 token。
        tokenBlacklist.revokeUser(id);
    }

    /**
     * 垂直越权防护：
     * 1. 禁止当前操作者对自身执行角色/状态变更（防误锁死自己）；
     * 2. 当目标用户为 admin / super_admin 时，仅 SUPER_ADMIN 可操作，普通 admin 不可越权。
     */
    private void checkAdminOperation(User target) {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        Long operatorId = (details instanceof Long) ? (Long) details : null;
        if (operatorId != null && operatorId.equals(target.getId())) {
            throw new BusinessException("不能对自身执行该操作");
        }
        boolean targetIsAdmin = RoleConst.ADMIN.equals(target.getRole())
                || RoleConst.SUPER_ADMIN.equals(target.getRole());
        if (targetIsAdmin) {
            String operatorRole = resolveOperatorRole();
            if (!RoleConst.SUPER_ADMIN.equals(operatorRole)) {
                throw new BusinessException("无权操作管理员账号");
            }
        }
    }

    /** 从 SecurityContext 解析当前操作者角色（JwtAuthFilter 已写入 authorities） */
    private String resolveOperatorRole() {
        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        for (var a : authorities) {
            String auth = a.getAuthority();
            if ("ROLE_SUPER_ADMIN".equals(auth)) return RoleConst.SUPER_ADMIN;
            if ("ROLE_ADMIN".equals(auth)) return RoleConst.ADMIN;
        }
        return null;
    }

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
