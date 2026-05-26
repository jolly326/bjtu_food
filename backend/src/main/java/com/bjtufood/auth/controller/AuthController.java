package com.bjtufood.auth.controller;

import com.bjtufood.auth.dto.LoginReq;
import com.bjtufood.auth.dto.LoginResp;
import com.bjtufood.auth.dto.RegisterReq;
import com.bjtufood.auth.service.AuthService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "用户登录", description = "使用学号/工号和密码登录，返回 JWT Token 和用户基本信息")
    @PostMapping("/auth/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginReq req) {
        // 调用流程：AuthService.login(req)
        // 1. 根据 username 查用户
        // 2. bcrypt 校验密码
        // 3. 检查用户状态
        // 4. 生成 JWT Token（含 userId, role, username）
        // 5. 返回 LoginResp
        LoginResp resp = authService.login(req);
        return Result.success("登录成功", resp);
    }

    @Operation(summary = "用户注册", description = "注册新用户（默认学生角色），注册成功后自动登录返回 Token")
    @PostMapping("/auth/register")
    public Result<LoginResp> register(@Valid @RequestBody RegisterReq req) {
        // 调用流程：AuthService.register(req)
        // 1. 检查用户名唯一性
        // 2. bcrypt 加密密码
        // 3. 保存用户（角色默认 student）
        // 4. 生成 Token 返回
        LoginResp resp = authService.register(req);
        return Result.success("注册成功", resp);
    }

    @Operation(summary = "获取当前用户信息", description = "根据 JWT Token 获取当前登录用户的基本信息")
    @GetMapping("/auth/profile")
    public Result<?> profile() {
        // TODO: 从 SecurityContext 获取当前用户 userId，查询用户信息
        // 调用 UserService.getById(userId)
        // 返回：{ id, username, nickname, avatar, role, stallId }
        return Result.success("用户信息");
    }

    @Operation(summary = "修改个人信息", description = "修改昵称和头像")
    @PutMapping("/auth/profile")
    public Result<Void> updateProfile() {
        // TODO: 获取当前用户ID，更新昵称/头像
        return Result.success();
    }
}
