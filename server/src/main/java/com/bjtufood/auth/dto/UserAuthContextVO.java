package com.bjtufood.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UGC 准入上下文（跨域只读投影）：供 review / feedback 等域做「能否发 UGC」判定 ——
 * 他域**不直连** {@code UserMapper} 与 {@code auth.entity.User}。
 * <p>
 * 只暴露判定所需的三要素，不外泄邮箱等身份字段：认证态判据（bind_email 非空）由 auth 侧
 * {@code AuthStateUtil} 折算成布尔值后透出，判据真源仍唯一留在 auth。
 * {@code openid} 是微信 msgSecCheck v2 的必填参数（UGC 文本机检），非 auth 域自行解读、仅透传。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAuthContextVO {

    /** 用户ID */
    private Long userId;

    /** 是否已学号邮箱认证（= bind_email 非空） */
    private boolean verified;

    /** 微信 openid（历史邮箱注册账号可为空，机检按既有口径跳过放行） */
    private String openid;
}
