package com.bjtufood.auth.controller;

import com.bjtufood.auth.dto.AdminLoginReq;
import com.bjtufood.auth.dto.AdminLoginResp;
import com.bjtufood.auth.service.AuthService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台登录（方案 C，spec §5.y.5）
 * <p>
 * 管理员账号密码 + BCrypt + JWT，与小程序微信登录体系解耦。
 * 学生账号不通过本端点登录。
 */
@Tag(name = "18. 管理后台登录", description = "方案 C：管理员账号密码 + BCrypt + JWT，返回 { token, username, role }。")
@RestController
@RequestMapping("/auth/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    @Operation(
            summary = "管理后台登录",
            description = "管理员账号密码登录，返回 { token, username, role }。仅 role ∈ {admin, super_admin} 可登录。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "account": "admin",
                      "password": "admin123"
                    }
                    """)))
    )
    @PostMapping("/login")
    public Result<AdminLoginResp> login(@Valid @RequestBody AdminLoginReq req) {
        return Result.success(authService.adminLogin(req));
    }
}
