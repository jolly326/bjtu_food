package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 新增管理员账号请求
 * <p>
 * 仅超级管理员可见的管理员管理模块使用；账号设初始密码。
 */
@Data
@Schema(description = "新增管理员请求")
public class AdminCreateReq {

    @NotBlank(message = "账号不能为空")
    @Schema(description = "管理员账号（登录用）", example = "logistics01")
    private String username;

    @NotBlank(message = "昵称不能为空")
    @Schema(description = "昵称", example = "后勤管理员")
    private String nickname;

    @Schema(description = "校园邮箱（可选）", example = "logistics01@bjtu.edu.cn")
    @Pattern(regexp = "^$|^[A-Za-z0-9._%+-]+@bjtu\\.edu\\.cn$", message = "邮箱必须为 @bjtu.edu.cn 校园邮箱")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "初始密码", example = "admin123")
    private String password;
}
