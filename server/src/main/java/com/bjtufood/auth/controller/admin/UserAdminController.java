package com.bjtufood.auth.controller.admin;

import com.bjtufood.auth.dto.UserVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.result.AdminPageResult;
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

@Tag(name = "09. 后台用户管理", description = "系统管理员管理用户状态。需要管理员 token。")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class UserAdminController {

    private final UserService userService;

    @Operation(summary = "用户列表", description = "用途：后台分页查看用户。支持按 status 筛选，"
            + "以及 keyword（**昵称 / 账号 / 绑定邮箱**模糊匹配，便于按人定位）。"
            + "测试示例：/admin/users?page=1&pageSize=10&status=active&keyword=干饭")
    @GetMapping
    public Result<AdminPageResult<UserVO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "状态：active/disabled/deleted；不传 = 全部")
            @RequestParam(required = false) String status,
            @Parameter(description = "关键词：昵称 / 账号 / 绑定邮箱模糊匹配")
            @RequestParam(required = false) String keyword) {
        return Result.success(AdminPageResult.of(userService.listUsers(page, pageSize, status, keyword)));
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

}
