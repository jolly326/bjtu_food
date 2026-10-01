package com.bjtufood.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 管理端口令配置（类型化绑定，单一真源）。
 * <p>
 * <b>归属</b>：随 {@code auth} 域走——管理端口令是
 * {@code AdminTokenFilter} 的私有配置。
 * <p>
 * 此前 {@code admin.token} 由 {@code AdminTokenFilter} 以 {@code @Value} 读取；
 * 现改为类型化绑定并补 {@link #isConfigured()}——「未配置即 fail-closed 拒绝全部 /admin」
 * 这一判定原先内联在过滤器方法里，现成为可被复用与测试的显式判据。
 */
@ConfigurationProperties(prefix = "admin")
@Deprecated(since = "2026-09", forRemoval = true)
public class AdminProperties {
    // ⚠️ 冻结：管理端（Web 后台）口令配置，待后期整体重构时移除。本期保留可编译、保留功能，不删除。

    /** 管理端口令（环境变量 {@code ADMIN_TOKEN} 注入；未配置时 fail-closed 拒绝全部 /admin 请求） */
    private String token = "";

    /** 口令是否已配置（未配置时管理端必须 fail-closed，避免公网裸奔） */
    public boolean isConfigured() {
        return StringUtils.hasText(token);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
