package com.bjtufood.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * <p>
 * 对应数据库表：user
 * 全量用户即学生 —— 管理端账号独立于本表（`admin_account`），故本表无角色列。
 * <p>
 * 注：表名 `user` 为 MySQL 保留字，当前 MyBatis-Plus 生成语句与手写 XML 均可正常执行（评估：
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

    /**
     * 已认证绑定邮箱（仅存认证关系，可空）——**认证状态的唯一真源**：非空即已认证（可写 UGC），
     * NULL 即游客态；判据见 {@link com.bjtufood.auth.support.AuthStateUtil#isVerified(String)}。
     * <p>
     * 认证态不设冗余列（不得回流）—— 判据唯一真源即本列是否为 NULL。
     */
    @Schema(description = "已认证绑定邮箱（可空；非空即已认证，认证状态唯一真源）")
    private String bindEmail;

    /** 创建时间（DB 时钟：INSERT 由 `DEFAULT CURRENT_TIMESTAMP` 写入，应用层不写、不填充） */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /** 更新时间（DB 时钟：UPDATE 由 `ON UPDATE CURRENT_TIMESTAMP` 维护，应用层不写、不填充） */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
