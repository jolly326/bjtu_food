package com.bjtufood.common.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 安全事件实时告警（飞书 / 企业微信机器人 Webhook）。
 *
 * <p><b>为什么需要它</b>：「账号被拿下」这件事若无人知晓，攻击者可以慢慢删、慢慢改，
 * 直到下次登录才发现。告警把「有人在爆破」与「爆破成功了」变成**立刻可见**。
 *
 * <p><b>通道配置</b>：{@code alert.webhook-url}（环境变量 {@code ALERT_WEBHOOK_URL}）与
 * {@code alert.webhook-secret}（{@code ALERT_WEBHOOK_SECRET}，机器人开启签名校验后必填）。
 * 未配置地址时通道不启用，启动日志给出 WARN 提示（告警是增强能力，不阻断启动；但生产必须配置）。
 *
 * <p><b>推送口径</b>：
 * <ul>
 *   <li><b>只推送、不落库</b> —— 告警是实时信号，推送即达；不引入告警表、不设管理端告警面板；</li>
 *   <li><b>异步</b> —— 单线程守护线程池，告警链路绝不阻塞业务请求；</li>
 *   <li><b>3 秒超时</b> —— 连接与读取均设上限，推送失败只记 ERROR，不影响主流程；</li>
 *   <li><b>不推送敏感值</b> —— 调用方只传已脱敏的标题与明细（口令 / token / 密钥一律不进告警）；</li>
 *   <li><b>签名校验</b> —— 配置密钥后每条推送附带 {@code timestamp} 与 {@code sign}。</li>
 * </ul>
 *
 * @see AlertType 类型与级别口径
 */
@Slf4j
@Component
public class SecurityAlertNotifier {

    /** 推送超时（连接 / 读取），避免告警链路拖住线程 */
    private static final int TIMEOUT_MS = 3_000;

    /** 告警正文前缀，便于在群消息里一眼识别来源（品牌与学生端 / 管理后台一致，均为「知行食记」） */
    private static final String TITLE_PREFIX = "【知行食记 · 安全告警】";

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String webhookUrl;

    /** 机器人签名密钥（为空则不签名） */
    private final String webhookSecret;

    private final RestTemplate restTemplate;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "security-alert");
        thread.setDaemon(true);
        return thread;
    });

    public SecurityAlertNotifier(@Value("${alert.webhook-url:}") String webhookUrl,
                                 @Value("${alert.webhook-secret:}") String webhookSecret) {
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
        if (this.webhookUrl.isEmpty()) {
            log.warn("[ALERT] 安全告警通道未启用：未配置 alert.webhook-url（环境变量 ALERT_WEBHOOK_URL）");
        } else {
            log.info("[ALERT] 安全告警通道已启用（签名校验：{}）", this.webhookSecret.isEmpty() ? "关闭" : "开启");
        }
    }

    /**
     * 推一条安全告警：级别 / 类型决定信号强度，标题与明细描述发生了什么。
     *
     * <p>推送在告警线程池内完成。明细中的来源 IP 由调用方在**请求线程**取好传入 ——
     * 告警线程没有 Web 上下文，异步之后再取只会得到 {@code null}。
     *
     * @param type   告警类型（决定级别与类型标签，见 {@link AlertType}）
     * @param title  告警标题（不得含口令 / token / 密钥等敏感值）
     * @param detail 告警明细（同上）
     */
    public void notify(AlertType type, String title, String detail) {
        String text = buildText(type, title, detail);
        // 未配置通道时降级为本地日志（仍然留痕，便于事后检索；不静默丢弃）
        if (webhookUrl.isEmpty()) {
            log.warn("[ALERT] {} —— {}", title, detail);
            return;
        }
        executor.submit(() -> send(text));
    }

    /** 告警正文：级别键与类型标签取自 {@link AlertType}（同一类事件的严重度是固定的）。 */
    private static String buildText(AlertType type, String title, String detail) {
        StringBuilder text = new StringBuilder(TITLE_PREFIX)
                .append('[').append(type.severity().key()).append(']')
                .append(type.label())
                .append('\n')
                .append(title);
        if (detail != null && !detail.isBlank()) {
            text.append('\n').append(detail);
        }
        return text.toString();
    }

    private void send(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new LinkedHashMap<>();
            // 飞书签名校验：请求须带 timestamp 与 sign（timestamp 为秒级）
            if (!webhookSecret.isEmpty()) {
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                body.put("timestamp", timestamp);
                body.put("sign", sign(timestamp));
            }
            body.put("msg_type", "text");
            body.put("content", Map.of("text", text));
            restTemplate.postForEntity(webhookUrl, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            // 只记异常摘要，不记请求体（避免签名与地址进日志）
            log.error("[ALERT] 告警推送失败：{}", e.getClass().getSimpleName());
        }
    }

    /**
     * 飞书自定义机器人签名：以 {@code timestamp + "\n" + secret} 为 HMAC-SHA256 密钥、
     * 以**空字节**为消息计算摘要，再 Base64 编码。
     *
     * @param timestamp 秒级时间戳（与请求体中的 timestamp 必须一致）
     * @return Base64 编码的签名
     */
    private String sign(String timestamp) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec((timestamp + "\n" + webhookSecret).getBytes(StandardCharsets.UTF_8),
                HMAC_SHA256));
        return Base64.getEncoder().encodeToString(mac.doFinal(new byte[0]));
    }
}
