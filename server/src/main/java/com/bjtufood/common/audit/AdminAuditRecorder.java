package com.bjtufood.common.audit;

import com.bjtufood.common.alert.AlertType;
import com.bjtufood.common.alert.SecurityAlertNotifier;
import com.bjtufood.common.audit.entity.AdminAuditLog;
import com.bjtufood.common.audit.mapper.AdminAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 管理端操作审计的写入点。
 *
 * <p><b>覆盖口径</b>：{@code /admin/**} 下的**全部写操作**（POST / PUT / DELETE / PATCH）——
 * 由路径前缀 + HTTP 方法判定，不依赖逐方法标注解，因此**新增写端点自动纳入覆盖**，
 * 不存在「忘了挂审计」的漏挂面。
 *
 * <p><b>降级口径</b>：审计写入失败**不阻塞业务**（业务成功但审计失败会记 ERROR 并推送告警），
 * 避免「审计表故障拖垮管理端」；同时绝不静默 —— 静默失败等于留痕形同虚设。
 *
 * @see AdminAuditLog 表结构
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditRecorder {

    /** 落审计的 HTTP 方法：读请求不落库，避免审计表被浏览类请求刷爆 */
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    /** 路径中的纯数字段（用于提取目标资源 ID） */
    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("\\d+");

    private static final int MAX_METHOD_LENGTH = 8;

    private static final int MAX_PATH_LENGTH = 255;

    private static final int MAX_RESULT_LENGTH = 32;

    private static final int MAX_IP_LENGTH = 64;

    private static final int MAX_UA_LENGTH = 255;

    /** 快照列宽上限（与 {@code AuditSnapshot} 的序列化上限配合，双保险） */
    private static final int MAX_SNAPSHOT_LENGTH = 8_000;

    private final AdminAuditLogMapper adminAuditLogMapper;

    private final SecurityAlertNotifier securityAlertNotifier;

    /** 该请求是否需要落审计（写方法才落库） */
    public boolean isAuditable(String httpMethod) {
        return httpMethod != null && WRITE_METHODS.contains(httpMethod.toUpperCase(Locale.ROOT));
    }

    /**
     * 落一条审计记录。
     *
     * @param adminId     操作人账号 ID
     * @param httpMethod  HTTP 方法
     * @param path        应用内路径（已剥离 context-path）
     * @param result      {@code success} / {@code fail:<HTTP 状态码>}
     * @param ip          来源 IP
     * @param ua          User-Agent
     * @param beforeValue 变更前对象快照（JSON；无则 {@code null}）
     * @param afterValue  变更后对象快照（JSON；无则 {@code null}）
     */
    public void record(Long adminId, String httpMethod, String path, String result, String ip, String ua,
                       String beforeValue, String afterValue) {
        try {
            AdminAuditLog row = new AdminAuditLog();
            row.setAdminId(adminId);
            row.setHttpMethod(truncate(httpMethod, MAX_METHOD_LENGTH));
            row.setPath(truncate(path, MAX_PATH_LENGTH));
            row.setTargetId(extractTargetId(path));
            row.setResult(truncate(result, MAX_RESULT_LENGTH));
            row.setIp(truncate(ip, MAX_IP_LENGTH));
            row.setUa(truncate(ua, MAX_UA_LENGTH));
            row.setBeforeValue(truncate(beforeValue, MAX_SNAPSHOT_LENGTH));
            row.setAfterValue(truncate(afterValue, MAX_SNAPSHOT_LENGTH));
            adminAuditLogMapper.insert(row);
        } catch (Exception e) {
            log.error("[AUDIT] 审计写入失败：adminId={} {} {}", adminId, httpMethod, path, e);
            securityAlertNotifier.notify(AlertType.AUDIT_WRITE_FAILURE, "审计写入失败（留痕缺口）",
                    "adminId=" + adminId + " · " + httpMethod + " " + path);
        }
    }

    /**
     * 自路径中提取目标资源 ID：取**最后一个纯数字段**（如 {@code /admin/dishes/12} → {@code 12}）。
     *
     * @return 数字段；路径中无数字段时返回 {@code null}
     */
    private static String extractTargetId(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            if (NUMERIC_SEGMENT.matcher(segments[i]).matches()) {
                return segments[i];
            }
        }
        return null;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
