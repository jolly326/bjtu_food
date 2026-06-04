package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.auth.dto.LoginReq;
import com.bjtufood.auth.dto.LoginResp;
import com.bjtufood.auth.dto.ProfileUpdateReq;
import com.bjtufood.auth.dto.RegisterReq;
import com.bjtufood.auth.dto.UserStatsVO;
import com.bjtufood.auth.entity.EmailVerificationCode;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
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

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final UserMapper userMapper;
    private final EmailVerificationCodeMapper emailVerificationCodeMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FavoriteMapper favoriteMapper;
    private final ReviewMapper reviewMapper;
    private final ImageUrlUtil imageUrlUtil;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String createEmailCode(String email, String purpose) {
        String normalizedEmail = normalizeEmail(email);
        validateCampusEmail(normalizedEmail);
        String normalizedPurpose = normalizePurpose(purpose);
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        EmailVerificationCode record = new EmailVerificationCode();
        record.setEmail(normalizedEmail);
        record.setCodeHash(passwordEncoder.encode(code));
        record.setPurpose(normalizedPurpose);
        record.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        emailVerificationCodeMapper.insert(record);

        return code;
    }

    @Override
    public LoginResp login(LoginReq req) {
        User user;
        if (StringUtils.hasText(req.getPassword())) {
            user = loginByPassword(req);
        } else if (StringUtils.hasText(req.getCode())) {
            user = loginByEmailCode(req);
        } else {
            throw new BusinessException("请填写密码或邮箱验证码");
        }

        if ("disabled".equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        return toLoginResp(user);
    }

    @Override
    public LoginResp register(RegisterReq req) {
        if (userService.getByUsername(req.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }
        String email = normalizeEmail(req.getEmail());
        validateCampusEmail(email);
        if (userService.getByEmail(email) != null) {
            throw new BusinessException("邮箱已注册");
        }
        verifyEmailCode(email, req.getCode(), "register");

        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setRole(RoleConst.USER);
        user.setStatus("active");
        user.setLastLoginAt(LocalDateTime.now());
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
        return new LoginResp(
                token,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                imageUrlUtil.toAbsoluteUrl(user.getAvatar()),
                user.getRole());
    }

    private Map<String, Object> buildProfileMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        map.put("nickname", user.getNickname());
        map.put("avatar", imageUrlUtil.toAbsoluteUrl(user.getAvatar()));
        map.put("role", user.getRole());
        return map;
    }

    private User loginByPassword(LoginReq req) {
        String account = StringUtils.hasText(req.getAccount()) ? req.getAccount() : req.getEmail();
        if (!StringUtils.hasText(account)) {
            throw new BusinessException("密码登录请填写账号或邮箱");
        }
        User user = account.contains("@")
                ? userService.getByEmail(normalizeEmail(account))
                : userService.getByUsername(account.trim());
        if (user == null || !StringUtils.hasText(user.getPassword())
                || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        return user;
    }

    private User loginByEmailCode(LoginReq req) {
        String email = normalizeEmail(req.getEmail());
        validateCampusEmail(email);
        verifyEmailCode(email, req.getCode(), "login");

        User user = userService.getByEmail(email);
        if (user == null) {
            throw new BusinessException("该邮箱尚未注册");
        }
        return user;
    }

    private void verifyEmailCode(String email, String code, String purpose) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("验证码不能为空");
        }
        EmailVerificationCode record = emailVerificationCodeMapper.selectOne(
                new LambdaQueryWrapper<EmailVerificationCode>()
                        .eq(EmailVerificationCode::getEmail, email)
                        .eq(EmailVerificationCode::getPurpose, normalizePurpose(purpose))
                        .isNull(EmailVerificationCode::getUsedAt)
                        .orderByDesc(EmailVerificationCode::getCreatedAt)
                        .last("LIMIT 1"));
        if (record == null) {
            throw new BusinessException("验证码不存在或已使用");
        }
        if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("验证码已过期");
        }
        boolean matched = passwordEncoder.matches(code, record.getCodeHash())
                || code.equals(record.getCodeHash());
        if (!matched) {
            throw new BusinessException("验证码错误");
        }
        record.setUsedAt(LocalDateTime.now());
        emailVerificationCodeMapper.updateById(record);
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException("邮箱不能为空");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePurpose(String purpose) {
        return "register".equalsIgnoreCase(purpose) ? "register" : "login";
    }

    private void validateCampusEmail(String email) {
        if (!email.endsWith("@bjtu.edu.cn")) {
            throw new BusinessException("请使用北京交通大学校园邮箱");
        }
    }
}
