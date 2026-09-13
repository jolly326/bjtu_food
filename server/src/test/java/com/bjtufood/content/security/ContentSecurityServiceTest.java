package com.bjtufood.content.security;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.content.security.impl.ContentSecurityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * ContentSecurityService 单元测试（MockRestServiceServer，参照 WechatServiceTest 风格）。
 * <p>
 * 覆盖（产品定稿 2026-09-13 验收点）：
 * 1. msgSecCheck v2 按 result.suggest 三态判定（pass/review/risky），不只看 errcode；
 * 2. risky 统一抛 400「内容包含违规信息，请修改后重试」；
 * 3. imgSecCheck 87014 → 400「图片包含违规内容，无法上传」；
 * 4. stable_token 缓存：同一 token 有效期内两次机检仅请求一次 stable_token；
 * 5. openid 为空（历史学号账号边界）跳过检测放行；
 * 6. 图片超 1MB 大小兜底。
 */
class ContentSecurityServiceTest {

    private static final String STABLE_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/stable_token";
    private static final String MSG_SEC_CHECK_URL = "https://api.weixin.qq.com/wxa/msg_sec_check";
    private static final String IMG_SEC_CHECK_URL = "https://api.weixin.qq.com/wxa/img_sec_check";

    private MockRestServiceServer server;
    private ContentSecurityServiceImpl contentSecurityService;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        contentSecurityService = new ContentSecurityServiceImpl(restTemplate);
        // 注入配置（生产由 @Value 注入，单测用 ReflectionTestUtils 等价赋值）
        ReflectionTestUtils.setField(contentSecurityService, "appid", "wx-appid");
        ReflectionTestUtils.setField(contentSecurityService, "secret", "wx-secret");
    }

    private void expectStableToken() {
        server.expect(once(), requestTo(startsWith(STABLE_TOKEN_URL)))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"access_token\":\"tk-1\",\"expires_in\":7200}",
                        MediaType.TEXT_PLAIN));
    }

    private void expectMsgSecCheck(String body) {
        server.expect(once(), requestTo(startsWith(MSG_SEC_CHECK_URL)))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.scene").value(2))
                .andExpect(jsonPath("$.openid").value("oX-openid"))
                .andExpect(jsonPath("$.content").value("一份番茄炒蛋"))
                .andRespond(withSuccess(body, MediaType.TEXT_PLAIN));
    }

    @Test
    @DisplayName("msgSecCheck v2 suggest=pass → PASS（请求体含 version=2/scene/openid/content）")
    void shouldReturnPassOnSuggestPass() {
        expectStableToken();
        expectMsgSecCheck("{\"errcode\":0,\"errmsg\":\"ok\",\"result\":{\"suggest\":\"pass\",\"label\":100}}");

        SecSuggest suggest = contentSecurityService.detectText("oX-openid", "一份番茄炒蛋", 2);

        assertThat(suggest).isEqualTo(SecSuggest.PASS);
        server.verify();
    }

    @Test
    @DisplayName("msgSecCheck v2 suggest=review → REVIEW（errcode=0 但按 suggest 判定，不放过）")
    void shouldReturnReviewOnSuggestReview() {
        expectStableToken();
        expectMsgSecCheck("{\"errcode\":0,\"errmsg\":\"ok\",\"result\":{\"suggest\":\"review\",\"label\":200}}");

        SecSuggest suggest = contentSecurityService.detectText("oX-openid", "一份番茄炒蛋", 2);

        assertThat(suggest).isEqualTo(SecSuggest.REVIEW);
        server.verify();
    }

    @Test
    @DisplayName("msgSecCheck v2 suggest=risky → detectText 三态原语返回 RISKY（不拦截）")
    void shouldReturnRiskyOnSuggestRisky() {
        expectStableToken();
        expectMsgSecCheck("{\"errcode\":0,\"errmsg\":\"ok\",\"result\":{\"suggest\":\"risky\",\"label\":20001}}");

        assertThat(contentSecurityService.detectText("oX-openid", "一份番茄炒蛋", 2))
                .isEqualTo(SecSuggest.RISKY);
        server.verify();
    }

    @Test
    @DisplayName("checkText 主链路：suggest=risky 统一拦截 400「内容包含违规信息，请修改后重试」")
    void shouldThrowOnSuggestRisky() {
        expectStableToken();
        expectMsgSecCheck("{\"errcode\":0,\"errmsg\":\"ok\",\"result\":{\"suggest\":\"risky\",\"label\":20001}}");

        assertThatThrownBy(() -> contentSecurityService.checkText("oX-openid", "一份番茄炒蛋", 2))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(400);
                    assertThat(be.getMessage()).isEqualTo("内容包含违规信息，请修改后重试");
                });
        server.verify();
    }

    @Test
    @DisplayName("stable_token 缓存：两次机检仅请求一次 stable_token（缓存至过期前 5 分钟）")
    void shouldCacheStableTokenAcrossCalls() {
        // token 请求仅一次
        expectStableToken();
        // msg_sec_check 两次（每次机检一次）
        server.expect(once(), requestTo(startsWith(MSG_SEC_CHECK_URL)))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"errcode\":0,\"result\":{\"suggest\":\"pass\"}}", MediaType.TEXT_PLAIN));
        server.expect(once(), requestTo(startsWith(MSG_SEC_CHECK_URL)))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"errcode\":0,\"result\":{\"suggest\":\"pass\"}}", MediaType.TEXT_PLAIN));

        assertThat(contentSecurityService.detectText("oX-openid", "一份番茄炒蛋", 2)).isEqualTo(SecSuggest.PASS);
        assertThat(contentSecurityService.detectText("oX-openid", "再来一份", 2)).isEqualTo(SecSuggest.PASS);
        server.verify();
    }

    @Test
    @DisplayName("openid 为空（历史学号账号边界）→ 跳过检测放行 PASS，不发起任何网络请求")
    void shouldSkipWhenOpenidMissing() {
        // 无任何 expectation：若发起请求 MockRestServiceServer 会直接抛异常
        SecSuggest suggest = contentSecurityService.checkText(null, "一份番茄炒蛋", 2);

        assertThat(suggest).isEqualTo(SecSuggest.PASS);
        server.verify();
    }

    @Test
    @DisplayName("imgSecCheck errcode=87014 → 400「图片包含违规内容，无法上传」")
    void shouldThrowOnRiskyImage() {
        expectStableToken();
        server.expect(once(), requestTo(startsWith(IMG_SEC_CHECK_URL)))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"errcode\":87014,\"errmsg\":\"risky content hit\"}",
                        MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> contentSecurityService.checkImage(new byte[]{1, 2, 3}))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(400);
                    assertThat(be.getMessage()).isEqualTo("图片包含违规内容，无法上传");
                });
        server.verify();
    }

    @Test
    @DisplayName("imgSecCheck errcode=0 → 正常通过（不抛异常）")
    void shouldPassOnCleanImage() {
        expectStableToken();
        server.expect(once(), requestTo(startsWith(IMG_SEC_CHECK_URL)))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.TEXT_PLAIN));

        assertThatCode(() -> contentSecurityService.checkImage(new byte[]{1, 2, 3}))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    @DisplayName("图片超过 1MB（imgSecCheck 硬限制）→ 400 大小兜底文案，不发起网络请求")
    void shouldRejectOversizedImage() {
        byte[] oversized = new byte[(int) (1024L * 1024 + 1)];

        assertThatThrownBy(() -> contentSecurityService.checkImage(oversized))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(400);
                    assertThat(be.getMessage()).contains("1MB");
                });
        server.verify();
    }

    @Test
    @DisplayName("未配置 appid/secret（本地开发环境）→ 跳过机审放行 PASS，isConfigured=false")
    void shouldSkipWhenNotConfigured() {
        ReflectionTestUtils.setField(contentSecurityService, "appid", "");
        ReflectionTestUtils.setField(contentSecurityService, "secret", "");

        assertThat(contentSecurityService.isConfigured()).isFalse();
        assertThat(contentSecurityService.checkText("oX-openid", "一份番茄炒蛋", 2))
                .isEqualTo(SecSuggest.PASS);
        server.verify();
    }
}
