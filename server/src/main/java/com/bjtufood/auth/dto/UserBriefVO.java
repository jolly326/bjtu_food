package com.bjtufood.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息摘要（跨域只读契约：userId → 昵称 + 绝对头像 URL）。
 * <p>
 * 架构收口 P0-1：管理端列表（评价 / 反馈 / 纠错）此前直接 import
 * {@code auth.entity.User} + {@code auth.mapper.UserMapper} 批量 join 取昵称头像，
 * 属跨模块直连他域 Mapper。现由 {@code UserService.mapBriefByIds(Collection)} 下发本投影，
 * 调用方不再持有 User 实体与 UserMapper。
 * <p>
 * 与 {@link UserVO} 的差异：UserVO 是「用户管理页」的完整视图（含 username/status/bindEmail 等），
 * 本类只承载「内容署名展示」所需最小字段——跨域契约越窄，耦合面越小。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBriefVO {

    /** 用户ID（user.id，映射键） */
    private Long userId;

    /** 昵称（原值下发，展示端兜底逻辑与既有 join 口径一致） */
    private String nickname;

    /** 头像（经 ImageUrlUtil 转绝对 URL，与既有列表口径一致） */
    private String avatarUrl;
}
