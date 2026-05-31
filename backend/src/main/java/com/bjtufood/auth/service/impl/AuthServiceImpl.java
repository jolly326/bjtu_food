package com.bjtufood.auth.service.impl;

import com.bjtufood.auth.dto.LoginReq;
import com.bjtufood.auth.dto.LoginResp;
import com.bjtufood.auth.dto.RegisterReq;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.service.AuthService;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.constant.RoleConst;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResp login(LoginReq req) {
        User user = userService.getByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException("Invalid username or password");
        }
        if ("disabled".equals(user.getStatus())) {
            throw new BusinessException("User is disabled");
        }
        return toLoginResp(user);
    }

    @Override
    public LoginResp register(RegisterReq req) {
        if (userService.getByUsername(req.getUsername()) != null) {
            throw new BusinessException("Username already exists");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setRole(RoleConst.STUDENT);
        user.setStatus("active");
        userMapper.insert(user);
        return toLoginResp(user);
    }

    private LoginResp toLoginResp(User user) {
        String token = jwtUtil.createToken(user.getId(), user.getRole(), user.getUsername());
        return new LoginResp(token, user.getId(), user.getNickname(), user.getAvatar(), user.getRole(), user.getStallId());
    }
}
