package com.bjtufood.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * <p>
 * 对应数据库表：user
 * 全量用户即学生 —— 管理端账号独立于本表（`admin`），故本表无角色列。
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

    /**
     * 微信标识号（列 {@code wx_id}，唯一）：登录/建号用的账号标识 ——
     * 游客建号写 {@code wx_<openid 尾 16 位>}；注销改写 {@code deleted_<id>} 释放唯一键。
     */
    @Schema(description = "微信标识号（唯一）", example = "wx_oXXXXX_ab12cd34")
    private String wxId;

    /** 昵称（展示名，可改；注销置「已注销用户」实现内容匿名化） */
    @Schema(description = "昵称", example = "张三")
    private String name;

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
     * 绑定邮箱（列 {@code email}，唯一）——**认证状态的唯一真源**：非空即已认证（可写 UGC），
     * NULL 即游客态；判据见 {@link com.bjtufood.auth.support.AuthStateUtil#isVerified(String)}。
     * <p>
     * 认证态不设冗余列（不得回流）—— 判据唯一真源即本列是否为 NULL；
     * NULL 不占用唯一索引，置 NULL 即释放绑定。
     */
    @Schema(description = "绑定邮箱（可空；非空即已认证，认证状态唯一真源）", example = "20240001@bjtu.edu.cn")
    private String email;

    /**
     * 累计违规次数（机审 {@code risky} 命中 + 举报成立）。
     * <p>
     * 达阈值触发梯度处置（见 {@code ViolationConst}）：警告 → 限言 24 小时 → 限言 7 天 → 封禁；
     * 计数与处置动作直接 upsert 到本行（{@code violation_count} / {@code muted_until} / {@code status}）。
     */
    @Schema(description = "累计违规次数")
    private Integer violationCount;

    /**
     * 限言到期时刻：处于未来 = 限言中（**全部写端点 403，读不受限**）。
     * <p>
     * 与 {@code status} 分开：限言是「临时不能发言」而不是「账号被禁用」，
     * 用 status 表达会让限言到期后无法自动恢复（且会连读一起挡掉）。
     */
    @Schema(description = "限言到期时刻（未来时刻=限言中）")
    private LocalDateTime mutedUntil;

    /** 创建时间（DB 时钟：INSERT 由 `DEFAULT CURRENT_TIMESTAMP` 写入，应用层不写、不填充） */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /** 更新时间（DB 时钟：UPDATE 由 `ON UPDATE CURRENT_TIMESTAMP` 维护，应用层不写、不填充） */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
