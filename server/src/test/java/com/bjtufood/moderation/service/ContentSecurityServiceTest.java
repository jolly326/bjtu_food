package com.bjtufood.moderation.service;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.moderation.dto.SecSuggest;
import com.bjtufood.moderation.service.impl.ContentSecurityServiceImpl;
import com.bjtufood.wechat.config.WechatProperties;
import com.bjtufood.wechat.service.WechatAccessTokenProvider;
import com.bjtufood.wechat.service.impl.WechatAccessTokenProviderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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
 * {@link ContentSecurityService} 单元测试（MockRestServiceServer，参照 WechatServiceTest 风格）。
 * <p>
 * 覆盖（产品定稿 ；用户拍板「取消人工复核」后三态归一为「放行/拒绝」二态）：
 * 1. msgSecCheck v2 按 result.suggest 判定（pass/review → 放行，risky → 拒绝），不只看 errcode；
 * 2. risky 统一抛 400「内容包含违规信息，请修改后重试」；
 * 3. imgSecCheck 87014 → 400「图片包含违规内容，无法上传」；
 * 4. stable_token 缓存：同一 token 有效期内两次内容安全检测仅请求一次 stable_token；
 * 5. openid 为空（历史学号账号边界）跳过检测放行；
 * 6. 图片超 1MB 大小兜底。
 * <p>
 * 架构收口 P0-B（随包迁移 {@code content.security} → {@code moderation.service}）：
 * 本测试的<b>接线方式随之调整</b>——原先 appid/secret 与 stable_token 由被测服务自己持有
 * （{@code ReflectionTestUtils.setField(contentSecurityService, "appid", ...)}），
 * 现凭据已剥离到 {@link WechatAccessTokenProvider}，故测试改为：
 * <ol>
 *   <li>先构造<b>真实的</b> {@link WechatAccessTokenProviderImpl}（绑定同一个 MockRestServiceServer
 *       的 RestTemplate，使 stable_token 请求仍可被断言）；</li>
 *   <li>appid/secret 注入到 tokenProvider 上（凭据归位）；</li>
 *   <li>再以 {@code new ContentSecurityServiceImpl(restTemplate, tokenProvider)} 构造被测服务。</li>
 * </ol>
 * 相比原先「把凭据塞进被测服务内部」，新接线额外覆盖了「审核服务经凭据提供方取 token」这一
 * 真实协作路径——而这正是本次收口修正的耦合点。
 * <p>
 * 「未配置凭据」用例相应改为断言 {@code tokenProvider.isConfigured()}（凭据判据已归位到 wechat 域）。
 */
class ContentSecurityServiceTest {

    private static final String STABLE_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/stable_token";
    private static final String MSG_SEC_CHECK_URL = "https://api.weixin.qq.com/wxa/msg_sec_check";
    private static final String IMG_SEC_CHECK_URL = "https://api.weixin.qq.com/wxa/img_sec_check";

    private MockRestServiceServer server;
    private WechatProperties wechatProperties;
    private WechatAccessTokenProvider tokenProvider;
    private ContentSecurityServiceImpl contentSecurityService;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        // 凭据提供方与审核服务共用同一 RestTemplate，stable_token 请求才可被同一 server 断言
        wechatProperties = new WechatProperties();
        wechatProperties.setAppid("wx-appid");
        wechatProperties.setSecret("wx-secret");
        tokenProvider = new WechatAccessTokenProviderImpl(restTemplate, wechatProperties);
        contentSecurityService = new ContentSecurityServiceImpl(restTemplate, tokenProvider);
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

    /**
     * 防回归：安检失败的提示必须<b>带 errcode 且能指明归因</b>，不得再退回笼统的
     * 「内容安全检测服务暂不可用」——2026-10-02 起三次线上事故（40164 白名单 / 40125 凭据 /
     * 配置缺失）全都被这句话掩盖，导致每次都必须翻服务端日志才能定位。
     * <p>
     * errcode 不含敏感信息，透出到端上使「看到提示」=「知道怎么修」，零日志往返。
     */
    @Test
    @DisplayName("imgSecCheck errcode=40164（IP 未加白名单）→ 提示须直指「出口 IP 加入白名单」")
    void shouldAttributeIpWhitelistErrcode() {
        expectStableToken();
        server.expect(once(), requestTo(startsWith(IMG_SEC_CHECK_URL)))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"errcode\":40164,\"errmsg\":\"invalid ip\"}",
                        MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> contentSecurityService.checkImage(new byte[]{1, 2, 3}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("40164")
                .hasMessageContaining("IP 白名单");
        server.verify();
    }

    @Test
    @DisplayName("imgSecCheck errcode=40125（AppSecret 配错）→ 提示须指明凭据不匹配，而非笼统的「暂不可用」")
    void shouldAttributeCredentialErrcode() {
        expectStableToken();
        server.expect(once(), requestTo(startsWith(IMG_SEC_CHECK_URL)))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"errcode\":40125,\"errmsg\":\"invalid appsecret\"}",
                        MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> contentSecurityService.checkImage(new byte[]{1, 2, 3}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("40125")
                .hasMessageContaining("AppSecret");
        server.verify();
    }

    @Test
    @DisplayName("msgSecCheck errcode 未归类（如 45011）→ 提示须含 errcode 与接口名，便于一眼定位")
    void shouldAlwaysCarryErrcodeForUnclassifiedCodes() {
        expectStableToken();
        expectMsgSecCheck("{\"errcode\":45011,\"errmsg\":\"api minute-quota reach\"}");

        assertThatThrownBy(() -> contentSecurityService.detectText("oX-openid", "一份番茄炒蛋", 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("45011");
        server.verify();
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
    @DisplayName("msgSecCheck v2 suggest=review → 视为放行（归一为 PASS，非 risky，不再产生待复核语义）")
    void shouldTreatSuggestReviewAsPass() {
        expectStableToken();
        expectMsgSecCheck("{\"errcode\":0,\"errmsg\":\"ok\",\"result\":{\"suggest\":\"review\",\"label\":200}}");

        SecSuggest suggest = contentSecurityService.detectText("oX-openid", "一份番茄炒蛋", 2);

        // 用户拍板「取消人工复核」：review（疑似）直接放行，仅 risky 拒绝
        assertThat(suggest).isEqualTo(SecSuggest.PASS);
        assertThat(suggest).isNotEqualTo(SecSuggest.RISKY);
        server.verify();
    }

    @Test
    @DisplayName("checkText 主链路：suggest=review 直接放行，不抛异常（原「待复核」链路已退役）")
    void shouldAllowReviewOnCheckText() {
        expectStableToken();
        expectMsgSecCheck("{\"errcode\":0,\"errmsg\":\"ok\",\"result\":{\"suggest\":\"review\",\"label\":200}}");

        assertThatCode(() -> contentSecurityService.checkText("oX-openid", "一份番茄炒蛋", 2))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    @DisplayName("SecSuggest.fromValue 归一：pass/review → PASS（放行）；risky/未知/缺失 → RISKY（拒绝，fail-closed）")
    void shouldNormalizeSuggestValues() {
        assertThat(SecSuggest.fromValue("pass")).isEqualTo(SecSuggest.PASS);
        // 归一：review 不再产生「待复核」态，映射即为放行
        assertThat(SecSuggest.fromValue("review")).isEqualTo(SecSuggest.PASS);
        assertThat(SecSuggest.fromValue("risky")).isEqualTo(SecSuggest.RISKY);
        // 微信未来新增未知态一律拒绝（宁可误拦不放行）
        assertThat(SecSuggest.fromValue("new-state")).isEqualTo(SecSuggest.RISKY);
        assertThat(SecSuggest.fromValue(null)).isEqualTo(SecSuggest.RISKY);
    }

    @Test
    @DisplayName("msgSecCheck v2 suggest=risky → detectText 原语返回 RISKY（不拦截）")
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
    @DisplayName("stable_token 缓存：两次内容安全检测仅请求一次 stable_token（缓存至过期前 5 分钟）")
    void shouldCacheStableTokenAcrossCalls() {
        // token 请求仅一次
        expectStableToken();
        // msg_sec_check 两次（每次内容安全检测一次）
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
    @DisplayName("未配置 appid/secret（本地开发环境）→ 跳过内容安全检测放行 PASS，tokenProvider.isConfigured=false")
    void shouldSkipWhenNotConfigured() {
        wechatProperties.setAppid("");
        wechatProperties.setSecret("");

        assertThat(tokenProvider.isConfigured()).isFalse();
        assertThat(contentSecurityService.checkText("oX-openid", "一份番茄炒蛋", 2))
                .isEqualTo(SecSuggest.PASS);
        server.verify();
    }
}
