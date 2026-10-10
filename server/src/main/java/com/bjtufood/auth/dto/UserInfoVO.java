package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户信息视图对象（微信登录体系）——<b>小程序端</b>登录/资料体。
 * <p>
 * 作为 {@link LoginVO#getUserInfo()} 的小程序端账号信息返回体，
 * 也复用为 {@code GET /auth/profile} 的用户信息主体。
 * 字段均 camelCase；已认证状态由 {@code email} 派生（见下）。
 * <p>
 * <b>字段集恰 4 个</b>：{@code id}、{@code name}、{@code avatar}、{@code email}。
 * <p><b>设计约束</b>：下列字段均不进此 VO（避免冗余 / 零消费），统一保持 4 字段最小集：
 * <ul>
 *   <li>{@code verified} —— 由 {@code email} 非空派生，同源冗余；端上判据统一为 {@code email != null}；</li>
 *   <li>{@code wxId} —— 端上零消费的账号标识：保留在 {@code user} 表与 JWT 载荷（{@code wxId}，仅供日志），属服务端内部字段；</li>
 *   <li>{@code status} —— 端上零消费（登录侧 400 与 UGC 写侧 403 已拦截）；</li>
 *   <li>{@code createdAt} —— 端上零消费（个人信息编辑页与「我的主页」信息卡均不展示注册时间）。</li>
 * </ul>
 * <p>
 * 与 {@link UserVO}（管理端用户列表）字段高度相似但<b>不可合并</b>，差异登记如下：
 * <ul>
 *   <li>{@link UserVO} 额外汇总 {@code wechatBound}（是否绑定微信）、
 *       {@code status}（账号状态，管理端需展示与操作）；本类均无。</li>
 *   <li>两类的认证状态均<b>不作出参字段</b>：小程序端按 {@code email != null} 派生，
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
    private String name;

    @Schema(description = "头像URL")
    private String avatar;

    /** 校园邮箱的唯一出参来源（未认证为 null）；**同时是认证状态的唯一判据**（非空即已认证） */
    @Schema(description = "绑定邮箱（可空；非空即已认证，认证状态唯一真源）", example = "20240001@bjtu.edu.cn")
    private String email;
}
