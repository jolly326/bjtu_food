package com.bjtufood.auth.controller;

import com.bjtufood.auth.dto.EmailCodeReq;
import com.bjtufood.auth.dto.LoginReq;
import com.bjtufood.auth.dto.LoginResp;
import com.bjtufood.auth.dto.ProfileUpdateReq;
import com.bjtufood.auth.dto.RegisterReq;
import com.bjtufood.auth.dto.UserStatsVO;
import com.bjtufood.auth.service.AuthService;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "01. 认证与用户", description = "登录、注册、个人资料、用户统计。登录成功后将 data.token 填入 Knife4j Authorize。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "获取邮箱验证码",
            description = """
                    用途：向 @bjtu.edu.cn 校园邮箱发送 6 位验证码。
                    规则：同一邮箱同一用途 60 秒内不能重复发送，验证码 10 分钟有效。
                    注意：验证码通过邮件发送，不会在响应中返回。
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "email": "20240001@bjtu.edu.cn",
                      "purpose": "login"
                    }
                    """)))
    )
    @PostMapping("/auth/email-code")
    public Result<Map<String, String>> createEmailCode(@Valid @RequestBody EmailCodeReq req) {
        authService.createEmailCode(req.getEmail(), req.getPurpose());
        return Result.success(Map.of("message", "验证码已发送"));
    }

    @Operation(
            summary = "用户登录",
            description = """
                    用途：登录并获取 JWT Token。
                    规则：支持密码登录，也支持邮箱验证码登录。验证码通过 /auth/email-code 生成。
                    Knife4j 测试：密码登录可使用 20240001 / 123456；验证码登录需先获取 purpose=login 的验证码。
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "account": "20240001",
                      "password": "123456"
                    }
                    """)))
    )
    @PostMapping("/auth/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return Result.success(authService.login(req));
    }

    @Operation(
            summary = "用户注册",
            description = "用途：通过校园邮箱验证码创建普通用户，并设置登录密码。注册成功后直接返回 token 和用户信息。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "username": "20240002",
                      "email": "20240002@bjtu.edu.cn",
                      "code": "123456",
                      "password": "123456",
                      "nickname": "交大学子"
                    }
                    """)))
    )
    @PostMapping("/auth/register")
    public Result<LoginResp> register(@Valid @RequestBody RegisterReq req) {
        return Result.success(authService.register(req));
    }

    @Operation(
            summary = "获取当前用户资料",
            description = "用途：个人中心进入时读取当前登录用户的昵称、头像、角色。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/auth/profile")
    public Result<Map<String, Object>> profile() {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(authService.getProfile(userId));
    }

    @Operation(
            summary = "修改当前用户资料",
            description = "用途：修改昵称或头像。头像应先通过 /upload/image 获取 URL，再作为 avatar 保存。",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "nickname": "新的昵称",
                      "avatar": "/images/seed/dishes/tomato-egg.jpg"
                    }
                    """)))
    )
    @PutMapping("/auth/profile")
    public Result<Map<String, Object>> updateProfile(@Valid @RequestBody ProfileUpdateReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(authService.updateProfile(userId, req));
    }

    @Operation(
            summary = "获取当前用户统计",
            description = "用途：个人中心展示我的收藏数、我的评价数。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/auth/stats")
    public Result<UserStatsVO> stats() {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(authService.getUserStats(userId));
    }
}
