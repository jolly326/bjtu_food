package com.bjtufood.auth.controller;

import com.bjtufood.auth.dto.LoginReq;
import com.bjtufood.auth.dto.LoginResp;
import com.bjtufood.auth.dto.ProfileUpdateReq;
import com.bjtufood.auth.dto.RegisterReq;
import com.bjtufood.auth.dto.UserStatsVO;
import com.bjtufood.auth.service.AuthService;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 * <p>
 * 处理用户登录、注册等无需认证的接口。
 * 登录成功后返回 JWT Token，前端需在后续请求中携带。
 */
@Tag(name = "认证管理", description = "登录、注册、获取用户信息")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录/自动注册", description = "使用学号和密码登录，用户不存在则自动注册，返回 JWT Token 和用户基本信息")
    @PostMapping("/auth/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginReq req) {
        LoginResp resp = authService.login(req);
        return Result.success(resp);
    }

    @Operation(summary = "用户注册", description = "注册新用户（默认学生角色），注册成功后自动登录返回 Token")
    @PostMapping("/auth/register")
    public Result<LoginResp> register(@Valid @RequestBody RegisterReq req) {
        LoginResp resp = authService.register(req);
        return Result.success(resp);
    }

    @Operation(summary = "获取当前用户信息", description = "根据 JWT Token 获取当前登录用户的基本信息")
    @GetMapping("/auth/profile")
    public Result<Map<String, Object>> profile() {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(authService.getProfile(userId));
    }

    @Operation(summary = "修改个人信息", description = "修改昵称和头像（两个字段至少传一个）")
    @PutMapping("/auth/profile")
    public Result<Map<String, Object>> updateProfile(@Valid @RequestBody ProfileUpdateReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(authService.updateProfile(userId, req));
    }

    @Operation(summary = "获取用户统计", description = "获取当前用户的收藏数和评价数")
    @GetMapping("/auth/stats")
    public Result<UserStatsVO> stats() {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(authService.getUserStats(userId));
    }
}
