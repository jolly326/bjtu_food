package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户视图对象（VO）
 * <p>
 * 用于管理端展示用户列表信息，不包含密码等敏感字段
 */
@Data
@Schema(description = "用户视图对象（管理端用）")
public class UserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "学号/工号", example = "stu001")
    private String username;

    @Schema(description = "校园邮箱", example = "20240001@bjtu.edu.cn")
    private String email;

    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "角色", example = "student")
    private String role;

    @Schema(description = "状态", example = "active")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
