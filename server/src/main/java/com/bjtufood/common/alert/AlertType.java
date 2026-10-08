package com.bjtufood.common.alert;

/**
 * 安全告警类型。
 *
 * <p><b>为什么类型要进枚举而不是自由文本</b>：告警记录的用途是「复盘 + 面板筛选」——
 * 自由文本无法过滤也无法核对口径。类型键同时承载**级别**（同一类事件的严重度是固定的，
 * 由调用方每次自选级别只会漂移）。
 *
 * <p><b>级别口径</b>：
 * <ul>
 *   <li>{@code info} —— 正常但值得留痕的事件（如登录成功）；</li>
 *   <li>{@code warn} —— 账号安全配置或学生账号处置发生变化，需知悉但不紧急；</li>
 *   <li>{@code critical} —— 疑似攻击成功 / 留痕出现缺口，需立即处置。</li>
 * </ul>
 *
 * <p>级别与标签是**写入时的快照**（存库即存中文标签），面板展示不再依赖枚举类，
 * 因此后续重命名枚举常量不会让历史记录失去可读性。
 */
public enum AlertType {

    /** 管理端登录成功 —— 用于发现「不是本人的登录」 */
    LOGIN_SUCCESS("登录成功", Severity.INFO),

    /** 登录失败达锁定阈值 —— 疑似暴力破解 */
    LOGIN_LOCKOUT("登录失败达阈值", Severity.CRITICAL),

    /** 管理端启用动态口令 */
    MFA_ENABLED("动态口令启用", Severity.WARN),

    /** 管理端停用动态口令（防护降级，需知悉） */
    MFA_DISABLED("动态口令停用", Severity.WARN),

    /** 管理端口令修改（既有 token 全部失效） */
    PASSWORD_CHANGED("口令修改", Severity.WARN),

    /** 审计写入失败 —— 留痕出现缺口，必须立即可见 */
    AUDIT_WRITE_FAILURE("审计写入失败", Severity.CRITICAL),

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

        /** 存库用的级别键 */
        public String key() {
            return key;
        }

        /** 中文标签（面板展示用） */
        public String label() {
            return label;
        }
    }

    /** 类型键（存库 `alert_type` 列） */
    private final String key;

    /** 类型中文标签（存库即快照，面板不再反查枚举） */
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
