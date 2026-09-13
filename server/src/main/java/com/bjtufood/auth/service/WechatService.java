package com.bjtufood.auth.service;

import com.bjtufood.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * 微信小程序服务（spec §5.y.1）
 * <p>
 * 调用微信 jscode2session 接口，用 wx.login 的 code 换取 openid（+unionid 若有）。
 */
@Service
public class WechatService {

    private static final Logger log = LoggerFactory.getLogger(WechatService.class);

    /** 连接/读取超时（毫秒）：防止微信接口挂起时 HTTP 线程被无限期占用 */
    private static final int WECHAT_TIMEOUT_MS = 5000;

    private final RestTemplate restTemplate;

    /** 微信响应固定以 text/plain 返回，无法依赖 Content-Type 选转换器，故统一先取 String 再手工反序列化 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Spring 装配入口：使用默认超时配置的 RestTemplate。
     * 保留无参可注入语义，便于测试替换（MockRestServiceServer 需要受控 RestTemplate）。
     */
    public WechatService() {
        this(defaultRestTemplate());
    }

    /** 测试可注入构造：允许传入 MockRestServiceServer 绑定的 RestTemplate；生产装配走无参构造。 */
    public WechatService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(WECHAT_TIMEOUT_MS);
        factory.setReadTimeout(WECHAT_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    @Value("${wechat.code2session-url:https://api.weixin.qq.com/sns/jscode2session}")
    private String code2sessionUrl;

    /**
     * 登录凭证校验（code2Session）。
     *
     * @param code wx.login 临时凭证
     * @return 微信会话结果 { openid, unionid?, session_key }
     * @throws BusinessException code2Session 失败（未配置/接口错误/凭证无效）时 400
     */
    public WechatSession code2Session(String code) {
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            throw new BusinessException(400, "微信登录未配置（WECHAT_APPID / WECHAT_SECRET 缺失）");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("appid", appid);
        params.add("secret", secret);
        params.add("js_code", code);
        params.add("grant_type", "authorization_code");

        String url = UriComponentsBuilder.fromHttpUrl(code2sessionUrl).queryParams(params).toUriString();

        try {
            // 微信 jscode2session 实际返回 `Content-Type: text/plain`（非 application/json），
            // 若直接 getForObject(url, Map.class) 会因找不到可读 text/plain→Map 的
            // HttpMessageConverter 而抛 RestClientException，导致真实 code 登录也失败。
            // 故先按 String 读取（StringHttpMessageConverter 支持 text/plain 与 */*），再用 Jackson 解析，
            // 使解析不依赖上游 Content-Type（P0 修复）。
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isBlank()) {
                throw new BusinessException(400, "微信登录校验失败：响应为空");
            }
            Map<String, Object> resp = parseJsonBody(body);
            Integer errcode = parseErrcode(resp.get("errcode"));
            if (errcode != null && errcode != 0) {
                log.warn("code2Session 失败 errcode={} errmsg={}", errcode, resp.get("errmsg"));
                throw new BusinessException(400, "微信登录凭证无效或已过期，请重试");
            }
            String openid = (String) resp.get("openid");
            if (openid == null || openid.isBlank()) {
                throw new BusinessException(400, "微信登录校验失败：未返回 openid");
            }
            String unionid = (String) resp.get("unionid");
            String sessionKey = (String) resp.get("session_key");
            return new WechatSession(openid, unionid, sessionKey);
        } catch (BusinessException e) {
            // 业务异常原样抛出（如「凭证无效」），不在这里被统一包装吞掉语义
            throw e;
        } catch (ResourceAccessException e) {
            // 上游不可达（云托管出网失败 / DNS / 超时）：语义属服务端不可用，用 500 承载（api-design 错误码表允许 500），
            // 便于把「部署出网问题」与「用户凭证问题」区分开（AUD-BE-01 排障与 AUD-BE-03 语义修正）
            log.error("调用微信 code2Session 接口不可达（url={}）", maskUrl(url), e);
            throw new BusinessException(500, "微信登录服务暂不可用，请稍后重试");
        } catch (Exception e) {
            // 防御放宽：非 2xx、响应体结构异常等其余失败统一转 400，
            // 避免底层异常（如序列化错误）穿透暴露实现细节
            log.error("调用微信 code2Session 接口失败（url={}）", maskUrl(url), e);
            throw new BusinessException(400, "微信登录服务异常，请稍后重试");
        }
    }

    /**
     * 反序列化微信响应体（不经 HttpMessageConverter，故不受 Content-Type 影响）。
     * <p>
     * 解析失败（非 JSON / 空对象）时抛出 RuntimeException，由调用处 catch(Exception) 兜底转
     * 400「微信登录服务异常，请稍后重试」，保持既有语义分支不变。
     */
    private Map<String, Object> parseJsonBody(String body) {
        try {
            Map<String, Object> map = OBJECT_MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {
            });
            if (map == null || map.isEmpty()) {
                throw new BusinessException(400, "微信登录校验失败：响应为空");
            }
            return map;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 非 JSON（如网关 HTML 错误页）：语义同「结构异常」，交由外层兜底转 400
            throw new IllegalStateException("code2Session 响应非 JSON：" + e.getMessage(), e);
        }
    }

    /** 日志脱敏：隐藏 appid / secret / js_code，避免凭证与登录码进入日志（AUD-BE-03） */
    private String maskUrl(String url) {
        if (url == null) {
            return "";
        }
        return url
                .replaceAll("(secret=)[^&]*", "$1***")
                .replaceAll("(js_code=)[^&]*", "$1***")
                .replaceAll("(appid=)[^&]*", "$1***");
    }

    /**
     * 解析微信响应中的 errcode：不同网关/版本可能返回数字或字符串，
     * 强转 Number 会在字符串形态下抛 ClassCastException 导致 500，
     * 这里做 Number / String 双态安全解析，无法识别时返回 null（视为无错误码，由 openid 兜底校验）。
     */
    private Integer parseErrcode(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 微信会话结果 */
    public record WechatSession(String openid, String unionid, String sessionKey) {
    }
}
