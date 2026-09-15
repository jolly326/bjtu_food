package com.bjtufood.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * <p>
 * 对应数据库表：user
 * 全量用户即学生（user.role 列已于 2026-09-15 用户拍板退役，管理端走口令体系、无角色数据语义）。
 * <p>
 * 注：表名 `user` 为 MySQL 保留字，当前 MyBatis-Plus 生成语句与手写 XML 均可正常执行（2026-09-14 评估：
 * MybatisPlusConfig 未配置全局表名转义，MP 生成的 FROM user 与 ReviewMapper.xml 的 JOIN user u 实测均正常，
 * 因 `user` 在 MySQL 8 为非保留关键字，仅裸标识符场景需注意），故保持现状不加转义；
 * 若后续引入原生拼接 SQL 需注意转义。
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

    /** 状态：active（正常）/ disabled（禁用）/ deleted（已注销） */
    @Schema(description = "状态", example = "active")
    private String status;

    /** 微信 openid（静默登录取号依据，唯一） */
    @Schema(description = "微信 openid（静默登录取号依据，唯一）", example = "oXXXXX...")
    private String openid;

    /** 微信 unionid（同主体多应用，可空） */
    @Schema(description = "微信 unionid（可空）")
    private String unionid;

    /** 认证状态：0=游客未认证 / 1=已邮箱认证（verified 不进 JWT，后端按此实时判定） */
    @Schema(description = "认证状态：0=游客未认证 / 1=已邮箱认证", example = "0")
    private Integer verified;

    /** 已认证绑定邮箱（仅存认证关系，可空） */
    @Schema(description = "已认证绑定邮箱（仅存认证关系，可空）")
    private String bindEmail;

    /** 认证时间 */
    @Schema(description = "认证时间")
    private LocalDateTime verifiedAt;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
