package com.bjtufood.wechat.service;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.wechat.config.WechatProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Set;

/**
 * 微信登录会话服务（jscode2Session）：用 wx.login 的 code 换 openid / session_key。
 * <p>
 * （微信响应中的 `unionid` 字段不解析。）
 * <p>
 * <b>归属</b>：{@code wechat.service}。
 * 本类与 {@link WechatAccessTokenProvider} 同为<b>微信开放平台 API 客户端</b>（前者 jscode2Session、
 * 后者 stable_token），同域收口 —— {@code wechat} 域 = 微信平台集成，
 * 由 {@code auth}（登录）、{@code moderation}（内容审核）、{@code upload}（云存储）共同依赖，
 * 且自身<b>不反向依赖任何业务域</b>（由 ArchTests 规则锁定）。
 */
@Service
public class WechatService {

    private static final Logger log = LoggerFactory.getLogger(WechatService.class);

    /** 连接/读取超时（毫秒）：防止微信接口挂起时 HTTP 线程被无限期占用 */
    private static final int WECHAT_TIMEOUT_MS = 5000;

    /**
     * <b>可归因于端上 code</b>的 errcode 集合——只有这些才允许回 400「凭证无效…请重试」。
     * <p>
     * 语义判据：**该错误能由「重新 {@code wx.login} 取一个新 code」自愈**。
     * <ul>
     *   <li>{@code 40029} invalid code：code 不正确 / 已过期 / 不属于本 appid；</li>
     *   <li>{@code 40163} code been used：code 是一次性凭证，重复提交即失效。</li>
     * </ul>
     * 其余 errcode（{@code 40013} invalid appid、{@code 40125} invalid appsecret、
     * {@code 40164} 出网 IP 未加白名单等）都是<b>服务端配置或平台侧</b>问题，
     * 用户重试无意义，一律回 500「微信登录服务暂不可用」。详见
     * {@link #classifyCode2SessionError(Integer, Object)}。
     */
    private static final Set<Integer> USER_SIDE_ERRCODES = Set.of(40029, 40163);

    private final RestTemplate restTemplate;

    /**
     * 微信开放平台配置（类型化绑定）。
     * <p>
     * 配置统一由 {@link WechatProperties} 承载，本类只消费 ——
     * 若由各消费者自行绑定 {@code @Value}，同一份凭据与「是否已配置」判据会分裂成多处。
     */
    private final WechatProperties wechatProperties;

    /** 微信响应固定以 text/plain 返回，无法依赖 Content-Type 选转换器，故统一先取 String 再手工反序列化 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Spring 装配入口：使用默认超时配置的 RestTemplate。
     * <p>
     * <b>必须显式 {@code @Autowired}</b>：本类有两个构造器（生产装配 / 测试注入），
     * Spring 在多构造器且无默认构造器时无法自动选择，会导致上下文加载失败。
     */
    @Autowired
    public WechatService(WechatProperties wechatProperties) {
        this(defaultRestTemplate(), wechatProperties);
    }

    /**
     * 测试可注入构造：允许传入 MockRestServiceServer 绑定的 RestTemplate 与受控配置对象；
     * 生产装配走 {@link #WechatService(WechatProperties)}。
     */
    public WechatService(RestTemplate restTemplate, WechatProperties wechatProperties) {
        this.restTemplate = restTemplate;
        this.wechatProperties = wechatProperties;
    }

    private static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(WECHAT_TIMEOUT_MS);
        factory.setReadTimeout(WECHAT_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    /**
     * 登录凭证校验（code2Session）。
     *
     * @param code wx.login 临时凭证
     * @return 微信会话结果 { openid, session_key }
     * @throws BusinessException code2Session 失败（未配置/接口错误/凭证无效）时 400
     */
    public WechatSession code2Session(String code) {
        if (!wechatProperties.isConfigured()) {
            throw new BusinessException(400, "微信登录未配置（WECHAT_APPID / WECHAT_SECRET 缺失）");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("appid", wechatProperties.getAppid());
        params.add("secret", wechatProperties.getSecret());
        params.add("js_code", code);
        params.add("grant_type", "authorization_code");

        String url = UriComponentsBuilder.fromHttpUrl(wechatProperties.getCode2sessionUrl())
                .queryParams(params).toUriString();

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
                throw classifyCode2SessionError(errcode, resp.get("errmsg"));
            }
            String openid = (String) resp.get("openid");
            if (openid == null || openid.isBlank()) {
                throw new BusinessException(400, "微信登录校验失败：未返回 openid");
            }
            // unionid 不再解析（多应用预留撤销）
            String sessionKey = (String) resp.get("session_key");
            return new WechatSession(openid, sessionKey);
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
     * 按 errcode 把 {@code code2Session} 失败分为「用户 code 问题」与「服务端/平台问题」两类。
     * <p>
     * <b>为何必须分类</b>：若把所有非 0 errcode 一律压成
     * 400「微信登录凭证无效或已过期，请重试」，则 {@code 40125 invalid appsecret}
     * （{@code WECHAT_APPID/WECHAT_SECRET} 配错）这类<b>服务端配置问题</b>会被误报为「凭证过期」——
     * 用户无论重试多少次都不可能成功，端侧与排障也会朝错误方向走。
     * <p>
     * <b>分类口径</b>：
     * <ul>
     *   <li><b>用户侧</b>（40029 code 不正确/已过期、40163 code 已被使用）⇒ 400 + 「凭证无效…请重试」。
     *       这两类确由端上 code 引起，重试有意义（{@code wx.login} 会换新 code）。</li>
     *   <li><b>服务端/平台侧</b>（40013 invalid appid、<b>40125 invalid appsecret</b>、
     *       40164 出网 IP 未加白名单、45011 频率限制等其余一切）⇒ 500 + 「微信登录服务暂不可用…」。
     *       用户侧无从处置，重试只会放大失败。</li>
     * </ul>
     * 该口径与 {@code WechatAccessTokenProviderImpl}（stable_token）一致：微信侧不可用一律 500，
     * 不把服务端责任转嫁为「用户凭证过期」。
     *
     * @param errcode 微信返回的错误码
     * @param errmsg  微信返回的原始描述（仅用于服务端日志，不外泄给端上）
     * @return 待抛出的业务异常（调用方直接 {@code throw}）
     */
    private BusinessException classifyCode2SessionError(Integer errcode, Object errmsg) {
        if (USER_SIDE_ERRCODES.contains(errcode)) {
            log.warn("code2Session 失败（端上 code 无效，重试可自愈）errcode={} errmsg={}", errcode, errmsg);
            return new BusinessException(400, "微信登录凭证无效或已过期，请重试");
        }
        log.error("code2Session 失败（服务端凭据/平台侧问题，重试无效，须修配置或白名单）errcode={} errmsg={}",
                errcode, errmsg);
        return new BusinessException(500, "微信登录服务暂不可用，请稍后重试");
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

    /** 微信会话结果（unionid 不再解析） */
    public record WechatSession(String openid, String sessionKey) {
    }
}
