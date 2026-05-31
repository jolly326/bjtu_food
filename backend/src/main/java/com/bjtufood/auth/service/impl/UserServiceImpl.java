package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.dto.UserVO;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public IPage<UserVO> listUsers(int page, int pageSize, String role, String status) {
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
    public void updateStatus(Long id, String status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("User not found");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public void updateRole(Long id, String role, Long stallId) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("User not found");
        }
        user.setRole(role);
        user.setStallId(stallId);
        userMapper.updateById(user);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
