package com.bjtufood.common.alert;

/**
 * 安全告警类型。
 *
 * <p><b>为什么类型要进枚举而不是自由文本</b>：告警类型的用途是「按固定口径识别事件 + 决定是否立刻处置」——
 * 自由文本无法核对口径，也无法保证同一类事件的严重度一致。类型键同时承载**级别**
 * （同一类事件的严重度是固定的，由调用方每次自选级别只会漂移）。
 *
 * <p><b>级别口径</b>：
 * <ul>
 *   <li>{@code info} —— 正常但值得知悉的事件（如登录成功）；</li>
 *   <li>{@code warn} —— 账号安全配置或学生账号处置发生变化，需知悉但不紧急；</li>
 *   <li>{@code critical} —— 疑似攻击成功，需立即处置。</li>
 * </ul>
 *
 * <p>告警正文由 {@link SecurityAlertNotifier} 组装为「级别标签 + 类型标签 + 标题 + 明细」推送到
 * webhook，级别与标签因此仍在推送内容里可见。口径真源见 {@code docs/secur/web/告警.md}。
 */
public enum AlertType {

    /** 管理端登录成功 —— 用于发现「不是本人的登录」 */
    LOGIN_SUCCESS("登录成功", Severity.INFO),

    /** 登录失败达锁定阈值 —— 疑似暴力破解 */
    LOGIN_LOCKOUT("登录失败达阈值", Severity.CRITICAL),

    /** 管理端口令修改（既有 token 全部失效） */
    PASSWORD_CHANGED("口令修改", Severity.WARN),

    /** 学生账号违规累积处置（自动限言 / 封禁） */
    VIOLATION_PENALTY("违规累积处置", Severity.WARN),

    /** 爬取特征命中（顺序枚举 ID / 批量命中不存在资源） */
    CRAWL_DETECTED("爬取检测", Severity.WARN);

    /** 告警级别（同一类型的事件固定同一级别）。 */
    public enum Severity {
        INFO("info", "提示"),
        WARN("warn", "警告"),
        CRITICAL("critical", "严重");

        private final String key;
        private final String label;

        Severity(String key, String label) {
            this.key = key;
            this.label = label;
        }

        /** 级别键（推送内容中展示，如 {@code critical}） */
        public String key() {
            return key;
        }

        /** 中文标签（推送内容中展示） */
        public String label() {
            return label;
        }
    }

    /** 类型键 */
    private final String key;

    /** 类型中文标签（推送内容中展示） */
    private final String label;

    private final Severity severity;

    AlertType(String label, Severity severity) {
        this.key = name();
        this.label = label;
        this.severity = severity;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public Severity severity() {
        return severity;
    }
}
