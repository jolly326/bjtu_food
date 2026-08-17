package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.dto.AdminCreateReq;
import com.bjtufood.auth.dto.UserVO;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.service.AdminManagerService;
import com.bjtufood.common.constant.RoleConst;
import com.bjtufood.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理员账号管理服务实现（仅超级管理员可用）
 */
@Service
@RequiredArgsConstructor
public class AdminManagerServiceImpl implements AdminManagerService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final com.bjtufood.auth.config.TokenBlacklist tokenBlacklist;

    @Override
    public IPage<UserVO> listAdmins(int page, int pageSize, String status) {
        int[] norm = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = norm[0]; pageSize = norm[1];
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getRole, RoleConst.ADMIN)
                .orderByDesc(User::getCreatedAt);
        if (StringUtils.hasText(status)) {
            wrapper.eq(User::getStatus, status);
        }
        IPage<User> p = userMapper.selectPage(new Page<>(page, pageSize), wrapper);
        IPage<UserVO> result = new Page<>(page, pageSize, p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAdmin(AdminCreateReq req) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername())) > 0) {
            throw new BusinessException("账号已存在");
        }
        if (StringUtils.hasText(req.getEmail())
                && userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail())) > 0) {
            throw new BusinessException("邮箱已存在");
        }
        User admin = new User();
        admin.setUsername(req.getUsername());
        admin.setNickname(req.getNickname());
        admin.setEmail(req.getEmail());
        admin.setPassword(passwordEncoder.encode(req.getPassword()));
        admin.setRole(RoleConst.ADMIN);
        admin.setStatus("active");
        userMapper.insert(admin);
        return admin.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("管理员不存在");
        }
        if (!RoleConst.ADMIN.equals(user.getRole())) {
            throw new BusinessException("该账号不是管理员");
        }
        if (!"active".equals(status) && !"disabled".equals(status)) {
            throw new BusinessException("非法的状态：" + status);
        }
        user.setStatus(status);
        userMapper.updateById(user);
        // 被禁用的管理员其已签发 token 必须立即失效；恢复 active 时解除拉黑
        if ("disabled".equals(status)) {
            tokenBlacklist.revokeUser(id);
        } else {
            tokenBlacklist.restoreUser(id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("管理员不存在");
        }
        if (!RoleConst.ADMIN.equals(user.getRole())) {
            throw new BusinessException("该账号不是管理员");
        }
        userMapper.deleteById(id);
        // 账号已删除，其已签发 token 必须立即失效
        tokenBlacklist.revokeUser(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String nickname, String password) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("管理员不存在");
        }
        if (!RoleConst.ADMIN.equals(user.getRole())) {
            throw new BusinessException("该账号不是管理员");
        }
        if (StringUtils.hasText(nickname)) {
            user.setNickname(nickname);
        }
        if (StringUtils.hasText(password)) {
            user.setPassword(passwordEncoder.encode(password));
        }
        userMapper.updateById(user);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
