package com.bjtufood.auth;

import com.bjtufood.auth.service.WechatService;
import com.bjtufood.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

/**
 * WechatService 回归测试（P0：code2Session 响应解析失败导致所有微信登录返回 400）。
 * <p>
 * 背景：微信 jscode2session 实测返回 {@code Content-Type: text/plain}，原始实现
 * {@code restTemplate.getForObject(url, Map.class)} 因找不到可读 text/plain→Map 的
 * HttpMessageConverter 直接抛 RestClientException，落入兜底分支 → 真实 code 登录同样失败。
 * <p>
 * 本测试用 MockRestServiceServer 构造 text/plain 响应，锁定「不依赖 Content-Type」的解析行为。
 */
class WechatServiceTest {

    private static final String URL = "https://api.weixin.qq.com/sns/jscode2session";

    private MockRestServiceServer server;
    private WechatService wechatService;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        wechatService = new WechatService(restTemplate);
        // 注入配置（生产由 @Value 注入，单测用 ReflectionTestUtils 等价赋值）
        ReflectionTestUtils.setField(wechatService, "appid", "wx-appid");
        ReflectionTestUtils.setField(wechatService, "secret", "wx-secret");
        ReflectionTestUtils.setField(wechatService, "code2sessionUrl", URL);
    }

    @Test
    @DisplayName("text/plain + errcode=40029 → 400 凭证无效（回归 P0：不再抛 400「服务异常」）")
    void shouldParseTextPlainErrorResponse() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(URL)))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"invalid code, rid: 66f8\"}",
                        MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> wechatService.code2Session("invalid-code"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(400);
                    assertThat(be.getMessage()).isEqualTo("微信登录凭证无效或已过期，请重试");
                });
        server.verify();
    }

    @Test
    @DisplayName("text/plain + openid → 成功解析出 openid（证明成功路径不被 Content-Type 阻断）")
    void shouldParseTextPlainSuccessResponse() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(URL)))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"openid\":\"oX\",\"session_key\":\"k\"}", MediaType.TEXT_PLAIN));

        WechatService.WechatSession session = wechatService.code2Session("valid-code");

        assertThat(session.openid()).isEqualTo("oX");
        assertThat(session.sessionKey()).isEqualTo("k");
        // unionid 已随 user.unionid 列退役（2026-09-16 零消费删除），WechatSession 不再携带该字段
        server.verify();
    }

    @Test
    @DisplayName("application/json 响应同样可解析（解析不依赖 Content-Type，双向兼容）")
    void shouldParseJsonContentTypeResponse() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(URL)))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"openid\":\"oJson\",\"unionid\":\"u1\",\"session_key\":\"sk\"}",
                        MediaType.APPLICATION_JSON));

        WechatService.WechatSession session = wechatService.code2Session("code");

        assertThat(session.openid()).isEqualTo("oJson");
        // 响应中的 unionid 字段不再解析（user.unionid 列已随 2026-09-16 零消费退役）
        assertThat(session.sessionKey()).isEqualTo("sk");
        server.verify();
    }

    @Test
    @DisplayName("text/plain + 无 openid 且无 errcode → 400 未返回 openid")
    void shouldRejectMissingOpenid() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(URL)))
                .andRespond(withSuccess("{\"errcode\":0}", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> wechatService.code2Session("code"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(400);
                    assertThat(be.getMessage()).isEqualTo("微信登录校验失败：未返回 openid");
                });
    }

    @Test
    @DisplayName("text/plain + 空响应体 → 400 响应为空")
    void shouldRejectEmptyBody() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(URL)))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> wechatService.code2Session("code"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(400);
                    assertThat(be.getMessage()).isEqualTo("微信登录校验失败：响应为空");
                });
    }

    @Test
    @DisplayName("非 JSON 响应（如网关 HTML）→ 400 服务异常（兜底语义不变）")
    void shouldFallbackOnNonJsonBody() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(URL)))
                .andRespond(withSuccess("<html>502 Bad Gateway</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> wechatService.code2Session("code"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(400);
                    assertThat(be.getMessage()).isEqualTo("微信登录服务异常，请稍后重试");
                });
    }

    @Test
    @DisplayName("上游不可达（超时）→ 500 微信登录服务暂不可用（语义分支保持）")
    void shouldReturn500WhenUpstreamUnreachable() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(URL)))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> wechatService.code2Session("code"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(500);
                    assertThat(be.getMessage()).isEqualTo("微信登录服务暂不可用，请稍后重试");
                });
    }

    @Test
    @DisplayName("未配置 appid/secret → 400 未配置（不进网络调用）")
    void shouldRejectWhenNotConfigured() {
        ReflectionTestUtils.setField(wechatService, "appid", "");
        ReflectionTestUtils.setField(wechatService, "secret", "");

        assertThatThrownBy(() -> wechatService.code2Session("code"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(400);
                    assertThat(be.getMessage()).contains("微信登录未配置");
                });
    }

    @Test
    @DisplayName("上游返回非 2xx（500）→ 400 服务异常（兜底语义不变）")
    void shouldFallbackOnNon2xxStatus() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(URL)))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> wechatService.code2Session("code"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(400);
                    assertThat(be.getMessage()).isEqualTo("微信登录服务异常，请稍后重试");
                });
    }
}
