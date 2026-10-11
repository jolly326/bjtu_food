package com.bjtufood.common.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 安全告警：类型与级别口径、webhook 推送内容、通道未配置时的降级。
 *
 * <p>告警**只推送、不落库**，故本类直接对真实 HTTP 端点断言推送体，不做任何持久层造桩。
 */
class SecurityAlertTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("类型集与级别一一固定：同一类型恒同一级别，级别与标签随推送可见")
    void typeDeterminesSeverity() {
        assertThat(AlertType.values())
                .extracting(Enum::name)
                .containsExactly("LOGIN_SUCCESS", "LOGIN_LOCKOUT", "PASSWORD_CHANGED",
                        "CRAWL_DETECTED");

        assertThat(AlertType.LOGIN_LOCKOUT.severity()).isEqualTo(AlertType.Severity.CRITICAL);
        assertThat(AlertType.LOGIN_LOCKOUT.key()).isEqualTo("LOGIN_LOCKOUT");
        assertThat(AlertType.LOGIN_LOCKOUT.label()).isEqualTo("登录失败达阈值");
        assertThat(AlertType.LOGIN_SUCCESS.severity().key()).isEqualTo("info");
        assertThat(AlertType.LOGIN_SUCCESS.severity().label()).isEqualTo("提示");
        assertThat(AlertType.PASSWORD_CHANGED.severity()).isEqualTo(AlertType.Severity.WARN);
    }

    @Test
    @DisplayName("已配置通道：异步推送到 webhook，正文含级别 / 类型 / 标题 / 明细，且带签名")
    void pushesToConfiguredWebhook() throws Exception {
        BlockingQueue<String> received = new ArrayBlockingQueue<>(1);
        HttpServer server = startServer(received);
        try {
            SecurityAlertNotifier notifier = new SecurityAlertNotifier(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/hook", "top-secret");
            notifier.notify(AlertType.CRAWL_DETECTED, "疑似 ID 枚举（顺序递增遍历）", "来源 IP=1.2.3.4");

            String body = received.poll(10, TimeUnit.SECONDS);
            assertThat(body).as("告警应在超时前推送").isNotNull();

            JsonNode json = MAPPER.readTree(body);
            assertThat(json.path("msg_type").asText()).isEqualTo("text");
            assertThat(json.path("timestamp").asText()).isNotBlank();
            assertThat(json.path("sign").asText()).isNotBlank();

            String text = json.path("content").path("text").asText();
            assertThat(text)
                    .contains("【知行食记 · 安全告警】")
                    .contains("[warn]")
                    .contains("爬取检测")
                    .contains("疑似 ID 枚举（顺序递增遍历）")
                    .contains("来源 IP=1.2.3.4");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("未配置通道：降级为本地日志，不抛异常也不阻塞（告警是旁路）")
    void unconfiguredChannelDoesNotThrow() {
        SecurityAlertNotifier notifier = new SecurityAlertNotifier("", "");

        assertThatCode(() -> notifier.notify(
                AlertType.LOGIN_SUCCESS, "管理端登录成功", "username=boss"))
                .doesNotThrowAnyException();
    }

    /** 起一个记录首个请求体的本地 HTTP 端点（端口由系统分配）。 */
    private static HttpServer startServer(BlockingQueue<String> received) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            received.offer(new String(body, StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();
        return server;
    }
}
