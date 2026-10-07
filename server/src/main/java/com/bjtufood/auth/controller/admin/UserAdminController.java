package com.bjtufood.auth.controller.admin;

import com.bjtufood.auth.dto.UserStatusReq;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "09. 后台用户管理", description = "系统管理员管理用户状态。需要管理员 token。")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class UserAdminController {

    private final UserService userService;

    @Operation(summary = "用户列表", description = "用途：后台分页查看用户。支持按 status 筛选，"
            + "以及 keyword（**昵称 / 账号 / 绑定邮箱**模糊匹配，便于按人定位）。"
            + "测试示例：/admin/users?page=1&pageSize=20&status=active&keyword=干饭")
    @GetMapping
    public Result<AdminPageResult<UserVO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "状态：active/disabled/deleted；不传 = 全部")
            @RequestParam(required = false) String status,
            @Parameter(description = "关键词：昵称 / 账号 / 绑定邮箱模糊匹配")
            @RequestParam(required = false) String keyword) {
        return Result.success(AdminPageResult.of(userService.listUsers(page, pageSize, status, keyword)));
    }

    @Operation(
            summary = "启用/禁用用户",
            description = "用途：修改用户账号状态（只改 status，取值为字符串枚举 active=启用 / disabled=禁用）。"
                    + "disabled 用户无法登录，且其已签发 token 即时失效。",
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
            @Parameter(description = "启用/禁用请求体 {status}；status 缺失/空白或非法返回 400")
            @Valid @RequestBody UserStatusReq req) {
        userService.updateStatus(id, req.getStatus());
        return Result.success();
    }

    @Operation(
            summary = "解绑认证邮箱",
            description = "用途：置空 bind_email（认证态唯一判据），账号立即回落游客态（UGC 写实时被拒 4031）。"
                    + "不改 status、不动 email（账号标识），登录与已发表内容不受影响；不代绑新邮箱。"
                    + "目标不存在返回 4001「用户不存在」；未绑定邮箱返回 400「该用户未绑定邮箱」。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content())
    )
    @DeleteMapping("/{id}/email")
    public Result<Void> unbindEmail(
            @Parameter(description = "用户ID", example = "1")
            @PathVariable Long id) {
        userService.unbindEmail(id);
        return Result.success();
    }

    @Operation(
            summary = "删除账号（注销）",
            description = "用途：管理端代用户注销 —— 与本人 DELETE /auth/account 完全同口径："
                    + "匿名化（昵称→已注销用户、username→deleted_{id}、avatar/email/openid/bind_email→NULL、status→deleted），"
                    + "非物理删除（user 行保留，历史评价/反馈归属不丢）；删除绑定邮箱验证码与全部站内通知；"
                    + "该 userId 全部已签发 token 拉黑。终态保护：已注销→400「账号已注销」，已禁用→400「账号已被禁用，无法注销」。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content())
    )
    @DeleteMapping("/{id}")
    public Result<Void> deleteAccount(
            @Parameter(description = "用户ID", example = "1")
            @PathVariable Long id) {
        userService.deleteAccount(id);
        return Result.success();
    }

}
