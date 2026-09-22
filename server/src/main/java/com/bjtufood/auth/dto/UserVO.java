package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户视图对象（VO）——<b>管理端</b>用户列表专用。
 * <p>
 * 与 {@link UserInfoVO}（小程序端登录/资料体）字段高度相似但<b>不可合并</b>，差异登记如下：
 * <ul>
 *   <li>本类额外汇总 {@code status}（账号状态，管理端需展示与操作）、{@code createdAt}（注册时间）、
 *       {@code wechatBound}（是否已绑定微信，仅布尔标识不暴露 openid 明文）——{@link UserInfoVO} 均无。</li>
 *   <li>认证状态<b>不作出参字段</b>：管理端与小程序端同口径，按 {@code bindEmail} 非空派生
 *       （2026-09-22 用户拍板：verified/verified_at 与 bind_email 同源冗余，DB 两列已退役）。</li>
 *   <li>消费方：{@code GET /admin/users}（UserAdminController）；{@link UserInfoVO} 消费方为
 *       {@code POST /auth/wechat-login}、{@code POST /auth/verify-email}、{@code GET /auth/profile}。</li>
 * </ul>
 * 不含密码等敏感字段。
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

    @Schema(description = "状态", example = "active")
    private String status;

    @Schema(description = "是否微信绑定（仅布尔标识，不返回 openid 明文，规避隐私泄露）", example = "false")
    private Boolean wechatBound;

    @Schema(description = "已认证绑定邮箱（可空；非空即已认证，认证状态唯一真源）", example = "20240001@bjtu.edu.cn")
    private String bindEmail;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
