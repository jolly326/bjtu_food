package com.bjtufood.common.alert;

import com.bjtufood.common.alert.entity.SecurityAlert;
import com.bjtufood.common.alert.mapper.SecurityAlertMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 安全告警记录的写入点：把一条告警落成可查询的行。
 *
 * <p><b>降级口径</b>：落库失败只记 ERROR，**不再推送告警**（推送告警本身会再写一行，
 * 形成递归）。告警记录是旁路，绝不能因为「记录写不进去」而让业务请求失败或线程被吞掉。
 *
 * <p><b>截断而非拒绝</b>：超长文本按列宽截断 —— 告警记录的价值在于「发生过什么」，
 * 因明细过长而整条丢失是本末倒置。
 *
 * @see SecurityAlertNotifier 调用方（异步落库 + 推送）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAlertRecorder {

    private static final int MAX_TYPE_LENGTH = 32;

    private static final int MAX_LABEL_LENGTH = 16;

    private static final int MAX_SEVERITY_LENGTH = 8;

    private static final int MAX_TITLE_LENGTH = 128;

    private static final int MAX_DETAIL_LENGTH = 1024;

    private static final int MAX_IP_LENGTH = 64;

    private final SecurityAlertMapper securityAlertMapper;

    /**
     * 组装一行告警记录（**不落库**）。
     *
     * <p>与落库分离，是为了让调用线程只做「取当前请求的 IP + 组装」这件极轻的事，
     * 真正的写库交给告警线程池，业务线程不被 DB 往返拖慢。
     *
     * @param type   告警类型（决定级别与标签）
     * @param title  告警标题（不得含敏感值）
     * @param detail 告警明细（同上）
     * @param ip     来源 IP（无 Web 上下文时传 null）
     */
    public SecurityAlert buildRow(AlertType type, String title, String detail, String ip) {
        SecurityAlert row = new SecurityAlert();
        row.setAlertType(truncate(type.key(), MAX_TYPE_LENGTH));
        row.setAlertTypeLabel(truncate(type.label(), MAX_LABEL_LENGTH));
        row.setSeverity(truncate(type.severity().key(), MAX_SEVERITY_LENGTH));
        row.setSeverityLabel(truncate(type.severity().label(), MAX_LABEL_LENGTH));
        row.setTitle(truncate(title, MAX_TITLE_LENGTH));
        row.setDetail(truncate(detail, MAX_DETAIL_LENGTH));
        row.setSourceIp(truncate(ip, MAX_IP_LENGTH));
        return row;
    }

    /**
     * 落库一条已组装好的告警记录。
     *
     * <p>在告警线程池中调用：**无外层事务**（除非调用方自己开了事务，而告警线程不属于任何请求事务），
     * 因此它不会因业务回滚而消失 —— 「告警发生过」本身就是事实，不该被撤销。
     */
    public void save(SecurityAlert row) {
        try {
            securityAlertMapper.insert(row);
        } catch (Exception e) {
            log.error("[ALERT] 告警记录写入失败：type={} title={}", row.getAlertType(), row.getTitle(), e);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
