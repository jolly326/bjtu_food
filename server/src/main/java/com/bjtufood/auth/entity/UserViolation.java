package com.bjtufood.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生账号违规与处置记录（表 {@code user_violation}，**只追加**）。
 *
 * <p><b>为什么需要独立留痕</b>：违规累积处置是**系统自动执行**的动作（不是某个管理员点的），
 * 若不单独留痕，「某个账号为什么被限言 / 被封」将无从复盘 —— 而封禁直接影响学生的使用，
 * 必须能回答「依据是什么、什么时候处置的」。
 *
 * <p><b>不含原文</b>：{@code detail} 只记来源与线索，不存违规内容全文（避免把违规文本
 * 二次留存到另一张表里）。
 */
@Data
@TableName("user_violation")
public class UserViolation {

    /** 自增 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户（{@code user.id}） */
    private Long userId;

    /** 来源：{@code moderation}（机审 risky 命中）/ {@code report}（举报成立） */
    private String source;

    /** 违规线索（脱敏摘要，不含原文全文） */
    private String detail;

    /**
     * 本次触发的处置动作：
     * {@code counted} / {@code warn} / {@code mute_24h} / {@code mute_7d} / {@code ban}。
     */
    private String action;

    /** 发生时间（DB 时钟写入） */
    private LocalDateTime createdAt;
}
