package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户信息视图对象（微信登录体系，spec §5.y.5）
 * <p>
 * 作为 {@link LoginResp#getUserInfo()} 的小程序端账号信息返回体，
 * 也复用为 {@code GET /auth/profile} 的用户信息主体。
 * 字段均 camelCase；`verified`/`bindEmail`/`guestShortId` 为微信登录体系新增语义。
 */
@Data
@Schema(description = "用户信息（微信登录体系）")
public class UserInfoVO {

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "学号/工号（游客建号为 wx_+openid 尾 16 位）", example = "20240001")
    private String username;

    @Schema(description = "校园邮箱（历史迁移凭证；微信游客可为空）", example = "20240001@bjtu.edu.cn")
    private String email;

    @Schema(description = "昵称", example = "食客0001")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "角色", example = "student")
    private String role;

    @Schema(description = "状态：active/disabled/deleted", example = "active")
    private String status;

    @Schema(description = "认证状态：true=已邮箱认证 / false=游客态", example = "false")
    private Boolean verified;

    @Schema(description = "已认证绑定邮箱（可空；仅存认证关系）", example = "20240001@bjtu.edu.cn")
    private String bindEmail;

    @Schema(description = "游客短标识（=「食客+ID 尾 4 位」）", example = "食客0001")
    private String guestShortId;
}
