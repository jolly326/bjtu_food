package com.bjtufood.auth.controller.admin;

import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "09. 后台用户管理", description = "系统管理员管理用户状态和角色。需要管理员 token。")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserAdminController {

    private final UserService userService;

    @Operation(summary = "用户列表", description = "用途：后台分页查看用户，支持按 role/status 筛选。测试示例：/admin/users?page=1&pageSize=10&role=student&status=active")
    @GetMapping
    public Result<?> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        return Result.success(userService.listUsers(page, pageSize, role, status));
    }

    @Operation(
            summary = "启用/禁用用户",
            description = "用途：修改用户账号状态。disabled 用户无法登录。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "status": "disabled"
                    }
                    """)))
    )
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @Parameter(description = "用户ID", example = "1")
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        userService.updateStatus(id, body.get("status"));
        return Result.success();
    }

    @Operation(
            summary = "修改用户角色",
            description = "用途：设置 student / admin。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "role": "admin"
                    }
                    """)))
    )
    @PutMapping("/{id}/role")
    public Result<Void> updateRole(
            @Parameter(description = "用户ID", example = "1")
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String role = (String) body.get("role");
        userService.updateRole(id, role);
        return Result.success();
    }
}
