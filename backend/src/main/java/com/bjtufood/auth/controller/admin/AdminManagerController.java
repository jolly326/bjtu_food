package com.bjtufood.auth.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.auth.dto.AdminCreateReq;
import com.bjtufood.auth.dto.UserVO;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.service.AdminManagerService;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员账号管理（仅超级管理员可见，接口级 SUPER_ADMIN 强校验）
 * <p>
 * 与「学生用户管理（/admin/users）」区分：本模块管理 ADMIN 账号的增删改禁。
 * 普通管理员访问本模块接口一律 403（me 查询除外，用于前端判断是否超管）。
 */
@Tag(name = "15. 后台管理员管理", description = "超级管理员维护 ADMIN 账号。需要 super_admin 角色 token。")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RestController
@RequestMapping("/admin/admins")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminManagerController {

    private final AdminManagerService adminManagerService;
    private final UserMapper userMapper;

    @Operation(summary = "管理员列表", description = "用途：查看全部 ADMIN 账号，支持按 status 筛选。测试示例：/admin/admins?page=1&pageSize=10")
    @GetMapping
    public Result<PageResult<UserVO>> listAdmins(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        IPage<UserVO> result = adminManagerService.listAdmins(page, pageSize, status);
        return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
    }

    @Operation(
            summary = "新增管理员",
            description = "用途：创建后勤/管理员账号并设初始密码。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "username": "logistics01",
                      "nickname": "后勤管理员",
                      "email": "logistics01@bjtu.edu.cn",
                      "password": "admin123"
                    }
                    """)))
    )
    @PostMapping
    public Result<Long> createAdmin(@Valid @RequestBody AdminCreateReq req) {
        return Result.success(adminManagerService.createAdmin(req));
    }

    @Operation(summary = "更新管理员（昵称 / 密码）", description = "用途：修改管理员昵称或重置密码。密码为空则不改密码。")
    @PutMapping("/{id}")
    public Result<Void> updateAdmin(
            @Parameter(description = "管理员ID", example = "2")
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        adminManagerService.update(id, body.get("nickname"), body.get("password"));
        return Result.success();
    }

    @Operation(summary = "启用/禁用管理员", description = "用途：禁用异常管理员账号，立即生效。")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @Parameter(description = "管理员ID", example = "2")
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        adminManagerService.updateStatus(id, body.get("status"));
        return Result.success();
    }

    @Operation(summary = "删除管理员", description = "用途：删除管理员账号。")
    @DeleteMapping("/{id}")
    public Result<Void> deleteAdmin(
            @Parameter(description = "管理员ID", example = "2")
            @PathVariable Long id) {
        adminManagerService.delete(id);
        return Result.success();
    }

    @Operation(summary = "当前管理员信息", description = "用途：返回当前登录管理员的基本信息（id/邮箱/昵称/角色）。所有管理员可查，前端据此判断是否超管以展示管理入口。")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/me")
    public Result<UserVO> me() {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.notFound("管理员不存在");
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        return Result.success(vo);
    }
}
