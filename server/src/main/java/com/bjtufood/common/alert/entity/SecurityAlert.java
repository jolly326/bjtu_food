package com.bjtufood.common.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安全告警记录（表 {@code security_alert}）。
 *
 * <p><b>为什么告警要落库</b>：群消息会被刷走、无法按类型回看 —— 落库后「这台机器上到底发生过什么」
 * 才是可查询、可筛选的事实。
 *
 * <p><b>只追加</b>：不提供修改 / 删除入口。
 *
 * <p><b>不含敏感值</b>：标题与明细由调用方传脱敏文本，口令 / token / 密钥 / 恢复码一律不入表。
 *
 * @see com.bjtufood.common.alert.SecurityAlertRecorder 写入点
 * @see com.bjtufood.common.alert.AlertType 类型与级别口径
 */
@Data
@TableName("security_alert")
public class SecurityAlert {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 告警类型键（{@link AlertType#key()}） */
    private String alertType;

    /** 类型中文标签（写入时快照，历史记录不随枚举重命名而失真） */
    private String alertTypeLabel;

    /** 级别键：{@code info} / {@code warn} / {@code critical} */
    private String severity;

    /** 级别中文标签（写入时快照） */
    private String severityLabel;

    /** 告警标题 */
    private String title;

    /** 告警明细（脱敏；按列宽截断存储） */
    private String detail;

    /** 来源 IP（调用线程无 Web 上下文时为 null） */
    private String sourceIp;

    /** 发生时间（**由 DB 时钟写入**：应用层不写、不填充） */
    private LocalDateTime createdAt;
}
