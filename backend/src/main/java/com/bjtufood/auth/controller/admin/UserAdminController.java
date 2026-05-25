package com.bjtufood.auth.controller.admin;

import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理控制器（系统管理员专用）
 * <p>
 * 用户列表查询、账号启用/禁用、角色修改等功能。
 * 所有接口需要 sys_admin 角色权限。
 */
@Tag(name = "用户管理（系统管理员）", description = "用户列表、帐号状态管理、角色修改")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserService userService;

    @Operation(summary = "用户列表", description = "分页查询所有用户，支持按角色和状态筛选")
    @GetMapping
    public Result<?> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        // TODO: 调用 UserService.listUsers(page, pageSize, role, status)
        // 返回分页用户列表（不含密码）
        return Result.success("用户列表");
    }

    @Operation(summary = "启用/禁用用户", description = "修改用户账号状态，disabled 状态的用户无法登录")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        // TODO: 调用 UserService.updateStatus(id, body.get("status"))
        // status 值：active（启用）/ disabled（禁用）
        return Result.success();
    }

    @Operation(summary = "修改用户角色", description = "修改用户角色。设置为 canteen_admin 时需传入 stallId")
    @PutMapping("/{id}/role")
    public Result<Void> updateRole(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        // TODO: 调用 UserService.updateRole(id, role, stallId)
        // body = { "role": "canteen_admin", "stallId": 1 }
        return Result.success();
    }
}
