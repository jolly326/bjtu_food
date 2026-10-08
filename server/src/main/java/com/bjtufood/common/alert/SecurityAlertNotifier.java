package com.bjtufood.common.alert;

import com.bjtufood.common.alert.entity.SecurityAlert;
import com.bjtufood.common.utils.ClientIpUtil;
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
 * <p><b>为什么需要它</b>：「账号被拿下」这件事目前没有任何人会知道 —— 攻击者可以慢慢删、
 * 慢慢改，直到下次登录才发现。告警把「有人在爆破」与「爆破成功了」变成**立刻可见**。
 *
 * <p><b>通道配置</b>：{@code alert.webhook-url}（环境变量 {@code ALERT_WEBHOOK_URL}）与
 * {@code alert.webhook-secret}（{@code ALERT_WEBHOOK_SECRET}，机器人开启签名校验后必填）。
 * 未配置地址时通道不启用，启动日志给出 WARN 提示（告警是增强能力，不阻断启动；但生产必须配置）。
 *
 * <p><b>推送口径</b>：
 * <ul>
 *   <li><b>异步</b> —— 单线程守护线程池，告警链路绝不阻塞业务请求；</li>
 *   <li><b>先落库、再推送</b> —— 每条告警都写成一行可查询记录（管理端「安全告警」面板据此回看），
 *       群消息会被刷走，库里的记录不会；</li>
 *   <li><b>3 秒超时</b> —— 连接与读取均设上限，推送失败只记 ERROR，不影响主流程；</li>
 *   <li><b>不推送敏感值</b> —— 调用方只传已脱敏的标题与明细（口令 / token / 密钥一律不进告警）；</li>
 *   <li><b>签名校验</b> —— 配置密钥后每条推送附带 {@code timestamp} 与 {@code sign}。</li>
 * </ul>
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

    /** 告警记录写入点（面板数据源） */
    private final SecurityAlertRecorder securityAlertRecorder;

    private final RestTemplate restTemplate;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "security-alert");
        thread.setDaemon(true);
        return thread;
    });

    public SecurityAlertNotifier(@Value("${alert.webhook-url:}") String webhookUrl,
                                 @Value("${alert.webhook-secret:}") String webhookSecret,
                                 SecurityAlertRecorder securityAlertRecorder) {
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.securityAlertRecorder = securityAlertRecorder;
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
     * 记一条安全告警：落库（面板可查）+ 推送（群消息即时可见）。
     *
     * <p>两者都在告警线程池内完成，业务线程只承担「取当前请求 IP + 组装行对象」这点开销。
     *
     * @param type   告警类型（决定级别与标签，见 {@link AlertType}）
     * @param title  告警标题（不得含口令 / token / 密钥等敏感值）
     * @param detail 告警明细（同上）
     */
    public void notify(AlertType type, String title, String detail) {
        // IP 必须在**调用线程**取：告警线程池没有 Web 上下文，异步后再取只会得到 null
        SecurityAlert row = securityAlertRecorder.buildRow(type, title, detail, ClientIpUtil.resolveCurrent());
        // 未配置通道时降级为本地日志（仍然留痕，便于事后检索；不静默丢弃）
        if (webhookUrl.isEmpty()) {
            log.warn("[ALERT] {} —— {}", title, detail);
        }
        executor.submit(() -> {
            securityAlertRecorder.save(row);
            if (!webhookUrl.isEmpty()) {
                send(title, detail);
            }
        });
    }

    private void send(String title, String detail) {
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
            body.put("content", Map.of("text", TITLE_PREFIX + title + "\n" + detail));
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
