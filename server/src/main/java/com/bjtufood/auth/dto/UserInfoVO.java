package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户信息视图对象（微信登录体系）——<b>小程序端</b>登录/资料体。
 * <p>
 * 作为 {@link LoginVO#getUserInfo()} 的小程序端账号信息返回体，
 * 也复用为 {@code GET /auth/profile} 的用户信息主体。
 * 字段均 camelCase；已认证状态由 {@code bindEmail} 派生（见下）。
 * <p>
 * <b>字段集恰 4 个</b>：{@code id}、{@code nickname}、{@code avatar}、{@code bindEmail}。
 * 以下字段已删除且不得回流：
 * <ul>
 *   <li>{@code verified}——`bindEmail` 非空的派生布尔，属同源冗余；
 *       端上判据统一为 {@code bindEmail != null}，DB 列 user.verified/verified_at 同批退役；</li>
 *   <li>{@code email}——微信体系下无写入点、恒为 NULL；</li>
 *   <li>{@code status}——端上零消费（登录侧 400 与 UGC 写侧 403 已拦截）；</li>
 *   <li>{@code guestShortId}——`id` 的纯派生值，改由消费端按 `id` 现算；</li>
 *   <li>{@code createdAt}——端上零消费（个人信息编辑页与「我的主页」信息卡均不展示注册时间）；</li>
 *   <li>{@code username}——**按「零消费即删」移出出参**：端上两处身份展示
 *       （「我的」页用户卡副行、「我的主页」信息卡 / 编辑页）均已统一渲染
 *       {@code bindEmail}（完整校园邮箱），不再渲染裸学号；`username` 出参端上零消费。
 *       账号标识本身仍保留在 {@code user} 表与 JWT 载荷（{@code username}，仅供日志），
 *       属服务端内部字段，不进公开出参。</li>
 * </ul>
 * <p>
 * 与 {@link UserVO}（管理端用户列表）字段高度相似但<b>不可合并</b>，差异登记如下：
 * <ul>
 *   <li>{@link UserVO} 额外汇总 {@code wechatBound}（是否绑定微信）、
 *       {@code status}（账号状态，管理端需展示与操作）；本类均无。</li>
 *   <li>两类的认证状态均<b>不作出参字段</b>：小程序端按 {@code bindEmail != null} 派生，
 *       管理端同口径派生（管理端需原始 0/1 时可自行判空）。</li>
 *   <li>消费方：{@code POST /auth/wechat-login}、{@code POST /auth/verify-email}、{@code GET /auth/profile}；
 *       {@link UserVO} 消费方为 {@code GET /admin/users}。</li>
 * </ul>
 */
@Data
@Schema(description = "用户信息（微信登录体系）")
public class UserInfoVO {

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "昵称", example = "食客0001")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatar;

    /** 校园邮箱的唯一出参来源（未认证为 null）；**同时是认证状态的唯一判据**（非空即已认证） */
    @Schema(description = "已认证绑定邮箱（可空；非空即已认证，认证状态唯一真源）", example = "20240001@bjtu.edu.cn")
    private String bindEmail;
}
