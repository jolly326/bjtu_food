package com.bjtufood.wechat.service;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.wechat.config.WechatProperties;
import com.bjtufood.wechat.constant.WechatApiConst;
import com.bjtufood.wechat.service.impl.WechatAccessTokenProviderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * {@link WechatAccessTokenProviderImpl} 的<b>失败文案契约</b>测试。
 *
 * <p><b>为什么单独锁文案</b>：本类被 {@code moderation} / {@code upload} / 登录三方共用，
 * 早期 6 个失败点全部抛「内容安全检测服务暂不可用」——上传场景下该措辞张冠李戴（实为取 token
 * 失败，与内容是否违规无关），且与 {@code ContentSecurityServiceImpl} 的真实安检失败撞文案，
 * 导致排查方向被带偏（2026-10-02 线上真实故障：用户上传图片即见「内容安全检测服务暂不可用」，
 * 实际是 IP 白名单 40164）。
 *
 * <p>本测试把「取 token / 调微信接口失败 ⇒ <b>微信服务</b>暂不可用」固化为契约，
 * 任何把它改回「内容安全检测」或引入第三种措辞的改动都会红。
 */
class WechatAccessTokenProviderImplTest {

    private MockRestServiceServer server;
    private WechatAccessTokenProvider provider;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        WechatProperties props = new WechatProperties();
        props.setAppid("wx-appid");
        props.setSecret("wx-secret");
        provider = new WechatAccessTokenProviderImpl(restTemplate, props);
    }

    /** 断言抛出的业务异常消息等于预期文案（500 + 逐字文案） */
    private void assertFailsWith(String expectedMessage) {
        assertThatThrownBy(provider::get)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(500);
                    assertThat(be.getMessage()).isEqualTo(expectedMessage);
                });
    }

    @Test
    @DisplayName("stable_token errcode 非 0（典型 40164 IP 白名单）→ 500「微信服务暂不可用」")
    void shouldSayWechatServiceOnErrcode() {
        // 40164 = 出网 IP 不在 API IP 白名单；这是 2026-10-02 线上故障的真实 errcode
        server.expect(requestTo(WechatApiConst.STABLE_TOKEN_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"errcode\":40164,\"errmsg\":\"invalid ip 8.149.1.1 not in whitelist\"}",
                        MediaType.TEXT_PLAIN));

        assertFailsWith("微信服务暂不可用，请稍后重试");
        server.verify();
    }

    @Test
    @DisplayName("stable_token 响应缺 access_token → 500「微信服务暂不可用」")
    void shouldSayWechatServiceOnMissingToken() {
        server.expect(requestTo(WechatApiConst.STABLE_TOKEN_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.TEXT_PLAIN));

        assertFailsWith("微信服务暂不可用，请稍后重试");
        server.verify();
    }

    @Test
    @DisplayName("响应为空 → 500「微信服务暂不可用」（不得因空响应暴露 NPE 或静默通过）")
    void shouldSayWechatServiceOnBlankBody() {
        server.expect(requestTo(WechatApiConst.STABLE_TOKEN_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        assertFailsWith("微信服务暂不可用，请稍后重试");
        server.verify();
    }

    @Test
    @DisplayName("响应非 JSON（如网关拦截 HTML）→ 500「微信服务暂不可用」")
    void shouldSayWechatServiceOnNonJsonBody() {
        server.expect(requestTo(WechatApiConst.STABLE_TOKEN_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess("<html>502 Bad Gateway</html>", MediaType.TEXT_HTML));

        assertFailsWith("微信服务暂不可用，请稍后重试");
        server.verify();
    }

    @Test
    @DisplayName("成功路径不受影响：仍返回 access_token（防止文案改动误伤主流程）")
    void shouldStillReturnTokenOnSuccess() {
        server.expect(requestTo(WechatApiConst.STABLE_TOKEN_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"T-abc\",\"expires_in\":7200}", MediaType.TEXT_PLAIN));

        assertThat(provider.get()).isEqualTo("T-abc");
        server.verify();
    }

    @Test
    @DisplayName("成功路径后的第二次调用走缓存，不重复请求（token 复用语义未变）")
    void shouldCacheTokenForSubsequentCalls() {
        server.expect(requestTo(WechatApiConst.STABLE_TOKEN_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"T-cache\",\"expires_in\":7200}", MediaType.TEXT_PLAIN));

        assertThat(provider.get()).isEqualTo("T-cache");
        // 第二次不再打微信（未再 expect 任何请求；verify() 校验无多余交互）
        assertThat(provider.get()).isEqualTo("T-cache");
        server.verify();
    }

    @Test
    @DisplayName("契约红线：失败文案**不得**再出现「内容安全」字样（防回退到旧的张冠李戴措辞）")
    void shouldNotReuseContentSecurityWording() {
        server.expect(requestTo(WechatApiConst.STABLE_TOKEN_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"errcode\":40164,\"errmsg\":\"x\"}", MediaType.TEXT_PLAIN));

        // 用字符串原生断言而非 hamcrest Matcher（AssertJ 的 isNot 不接受 Matcher 类型）
        assertThatThrownBy(provider::get)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessage())
                        .doesNotContain("内容安全"));
        server.verify();
    }
}