package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.auth.dto.LoginReq;
import com.bjtufood.auth.dto.LoginResp;
import com.bjtufood.auth.dto.ProfileUpdateReq;
import com.bjtufood.auth.dto.RegisterReq;
import com.bjtufood.auth.dto.UserStatsVO;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.service.AuthService;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.constant.RoleConst;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JwtUtil;
import com.bjtufood.favorite.entity.Favorite;
import com.bjtufood.favorite.mapper.FavoriteMapper;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FavoriteMapper favoriteMapper;
    private final ReviewMapper reviewMapper;
    private final ImageUrlUtil imageUrlUtil;

    @Override
    public LoginResp login(LoginReq req) {
        User user = userService.getByUsername(req.getUsername());

        // 用户不存在 → 自动注册（首次登录自动创建）
        if (user == null) {
            user = new User();
            user.setUsername(req.getUsername());
            user.setPassword(passwordEncoder.encode(req.getPassword()));
            user.setNickname(req.getUsername());
            user.setRole(RoleConst.STUDENT);
            user.setStatus("active");
            userMapper.insert(user);
        } else {
            // 用户存在 → 校验密码
            if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
                throw new BusinessException("用户名或密码错误");
            }
            if ("disabled".equals(user.getStatus())) {
                throw new BusinessException("账号已被禁用");
            }
        }

        return toLoginResp(user);
    }

    @Override
    public LoginResp register(RegisterReq req) {
        if (userService.getByUsername(req.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
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

    @Override
    public Map<String, Object> getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return buildProfileMap(user);
    }

    @Override
    public Map<String, Object> updateProfile(Long userId, ProfileUpdateReq req) {
        if (!StringUtils.hasText(req.getNickname()) && !StringUtils.hasText(req.getAvatar())) {
            throw new BusinessException("昵称和头像至少填写一项");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (StringUtils.hasText(req.getNickname())) {
            user.setNickname(req.getNickname());
        }
        if (StringUtils.hasText(req.getAvatar())) {
            user.setAvatar(req.getAvatar());
        }
        userMapper.updateById(user);
        return buildProfileMap(user);
    }

    @Override
    public UserStatsVO getUserStats(Long userId) {
        long favoriteCount = favoriteMapper.selectCount(
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, userId));
        long reviewCount = reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .eq(Review::getIsHidden, 0));
        return new UserStatsVO(favoriteCount, reviewCount);
    }

    private LoginResp toLoginResp(User user) {
        String token = jwtUtil.createToken(user.getId(), user.getRole(), user.getUsername());
        return new LoginResp(token, user.getId(), user.getNickname(), imageUrlUtil.toAbsoluteUrl(user.getAvatar()), user.getRole(), user.getStallId());
    }

    private Map<String, Object> buildProfileMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("nickname", user.getNickname());
        map.put("avatar", imageUrlUtil.toAbsoluteUrl(user.getAvatar()));
        map.put("role", user.getRole());
        return map;
    }
}
