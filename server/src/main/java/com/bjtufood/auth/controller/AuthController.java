package com.bjtufood.auth.controller;

import com.bjtufood.auth.dto.EmailCodeReq;
import com.bjtufood.auth.dto.LoginResp;
import com.bjtufood.auth.dto.ProfileUpdateReq;
import com.bjtufood.auth.dto.UserStatsVO;
import com.bjtufood.auth.dto.VerifyEmailReq;
import com.bjtufood.auth.dto.WechatLoginReq;
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

@Tag(name = "01. 认证与用户", description = "微信静默登录、学号邮箱认证、个人资料、用户统计。登录成功后将 data.token 填入 Swagger UI Authorize。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "获取邮箱验证码（认证用途）",
            description = """
                    用途：向 @bjtu.edu.cn 校园邮箱发送 6 位验证码，用于学号邮箱认证（purpose 仅 verify）。
                    校园邮箱 = {学号}@bjtu.edu.cn，传 username（学号）即可自动推导邮箱，无需填 email。
                    规则：同一邮箱 60 秒内不能重复发送，验证码 10 分钟有效。验证码经邮件发送，不会在响应中返回。
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "username": "20240001",
                      "purpose": "verify"
                    }
                    """)))
    )
    @PostMapping("/auth/email-code")
    public Result<Map<String, String>> createEmailCode(@Valid @RequestBody EmailCodeReq req) {
        authService.createEmailCode(req.getUsername(), req.getEmail(), req.getPurpose());
        return Result.success(Map.of("message", "验证码已发送"));
    }

    @Operation(
            summary = "微信静默登录",
            description = """
                    用途：小程序启动时调用 wx.login 获取 code，后端 code2Session 换 openid 自动登录。
                    新 openid 自动建号（游客态 verified=false）；已有 openid 直接返回原账号。返回 { token, userInfo }。
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "code": "0a3b...（wx.login 临时凭证）"
                    }
                    """)))
    )
    @PostMapping("/auth/wechat-login")
    public Result<LoginResp> wechatLogin(@Valid @RequestBody WechatLoginReq req) {
        return Result.success(authService.wechatLogin(req.getCode()));
    }

    @Operation(
            summary = "学号邮箱认证（绑定/合并/替换）",
            description = """
                    用途：游客完成学号邮箱认证（verified=true）。入参仅验证码，绑定邮箱由验证码记录推导，当前微信账号从登录态取。
                    认证通过后：无历史邮箱则直接绑定；存在历史邮箱账号则数据归属转移（旧账号业务数据改挂到当前微信）；
                    邮箱已被他微信绑定则替换绑定（旧微信 verified=false、bind_email=NULL）。返回更新后 { token, userInfo }。
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "code": "123456"
                    }
                    """)))
    )
    @PostMapping("/auth/verify-email")
    public Result<LoginResp> verifyEmail(@Valid @RequestBody VerifyEmailReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(authService.verifyEmail(req.getCode(), userId));
    }

    @Operation(
            summary = "获取当前用户资料",
            description = "用途：个人中心进入时读取当前登录用户的昵称、头像、角色、认证状态（verified）、绑定邮箱（bindEmail）、游客短标识（guestShortId）。",
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
