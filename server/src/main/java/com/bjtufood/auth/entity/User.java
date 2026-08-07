package com.bjtufood.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * <p>
 * 对应数据库表：user
 * 包含两种角色：student（学生）、admin（管理员）
 */
@Data
@TableName("user")
@Schema(description = "用户")
public class User {

    @TableId(type = IdType.AUTO)
    @Schema(description = "用户ID")
    private Long id;

    /** 学号/工号（登录用，唯一） */
    @Schema(description = "学号/工号", example = "stu001")
    private String username;

    /** 校园邮箱，注册和验证码登录使用 */
    @Schema(description = "校园邮箱", example = "20240001@bjtu.edu.cn")
    private String email;

    /** 兼容字段：验证码登录模式下可为空 */
    @Schema(description = "密码哈希（验证码登录模式下可为空）")
    private String password;

    /** 昵称 */
    @Schema(description = "昵称", example = "张三")
    private String nickname;

    /** 头像 URL */
    @Schema(description = "头像URL")
    private String avatar;

    /** 角色：student / admin */
    @Schema(description = "角色", example = "student")
    private String role;

    /** 状态：active（正常）/ disabled（禁用） */
    @Schema(description = "状态", example = "active")
    private String status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    /** 最近登录时间 */
    @Schema(description = "最近登录时间")
    private LocalDateTime lastLoginAt;
}
