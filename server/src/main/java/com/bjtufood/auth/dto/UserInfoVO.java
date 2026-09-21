package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户信息视图对象（微信登录体系，spec §5.y.5）——<b>小程序端</b>登录/资料体。
 * <p>
 * 作为 {@link LoginResp#getUserInfo()} 的小程序端账号信息返回体，
 * 也复用为 {@code GET /auth/profile} 的用户信息主体。
 * 字段均 camelCase；`verified`/`bindEmail` 为微信登录体系新增语义。
 * <p>
 * <b>字段集恰为 6 个</b>（2026-09-21 spec §7.32 / `auth-api-contract`）：{@code id}、{@code username}、
 * {@code nickname}、{@code avatar}、{@code verified}、{@code bindEmail}。以下字段已删除且不得回流：
 * {@code email}（微信体系下无写入点、恒为 NULL）、{@code status}（端上零消费）、
 * {@code guestShortId}（`id` 的纯派生值，改由消费端按 `id` 现算）。
 * <p>
 * 与 {@link UserVO}（管理端用户列表）字段高度相似但<b>不可合并</b>，差异登记如下：
 * <ul>
 *   <li>{@code verified} 类型不同：本类为 {@code Boolean}（true=已认证 / false=游客态，端上语义）；
 *       {@link UserVO} 为 {@code Integer}（0/1 原始库值）。</li>
 *   <li>{@link UserVO} 额外含 {@code createdAt}（注册时间）与 {@code wechatBound}（是否绑定微信），本类无。</li>
 *   <li>消费方：{@code POST /auth/wechat-login}、{@code POST /auth/verify-email}、{@code GET /auth/profile}；
 *       {@link UserVO} 消费方为 {@code GET /admin/users}。</li>
 * </ul>
 */
@Data
@Schema(description = "用户信息（微信登录体系）")
public class UserInfoVO {

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "学号/工号（游客建号为 wx_+openid 尾 16 位）", example = "20240001")
    private String username;

    @Schema(description = "昵称", example = "食客0001")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "认证状态：true=已邮箱认证 / false=游客态", example = "false")
    private Boolean verified;

    /** 校园邮箱的唯一出参来源（未认证为 null） */
    @Schema(description = "已认证绑定邮箱（可空；仅存认证关系）", example = "20240001@bjtu.edu.cn")
    private String bindEmail;
}
