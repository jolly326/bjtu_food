package com.bjtufood.content.security.impl;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.content.security.ContentSecurityService;
import com.bjtufood.content.security.SecSuggest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 微信内容安全检测服务实现（msgSecCheck v2 / imgSecCheck / stable_token）。
 * <p>
 * 判定口径（红线）：
 * <ul>
 *   <li>文本以 {@code result.suggest} 三态判定，不得只看 errcode（errcode=0 仅代表调用成功）；</li>
 *   <li>图片以 errcode 判定：0=通过，87014=违规，其余=调用失败 fail-closed；</li>
 *   <li>risky 统一在本服务拦截为 400「内容包含违规信息，请修改后重试」，文案不散落调用方；</li>
 *   <li>上游不可达/调用失败 fail-closed（500），保证入库内容必过机审（产品定稿「全部 UGC 过检」）；</li>
 *   <li>未配置 appid/secret（本地开发环境）跳过机审放行，生产必须配置（部署检查项）。</li>
 * </ul>
 */
@Service
public class ContentSecurityServiceImpl implements ContentSecurityService {

    private static final Logger log = LoggerFactory.getLogger(ContentSecurityServiceImpl.class);

    /** 连接/读取超时（毫秒）：与 WechatService 一致，防止微信接口挂起拖垮 UGC 主链路 */
    private static final int WECHAT_TIMEOUT_MS = 5000;

    /** msgSecCheck v2 内容长度上限（字） */
    private static final int MAX_TEXT_LENGTH = 2500;

    /** imgSecCheck 图片大小上限（字节，1MB） */
    public static final long MAX_IMAGE_BYTES = 1024L * 1024;

    private static final String MSG_SEC_CHECK_URL = "https://api.weixin.qq.com/wxa/msg_sec_check";
    private static final String IMG_SEC_CHECK_URL = "https://api.weixin.qq.com/wxa/img_sec_check";
    private static final String STABLE_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/stable_token";

    /** stable_token 缓存提前失效余量：过期前 5 分钟即视为失效（红线要求 ≥5 分钟） */
    private static final long TOKEN_EXPIRE_MARGIN_MS = 5 * 60 * 1000L;

    private final RestTemplate restTemplate;

    /** 微信响应固定以 text/plain 返回，统一先取 String 再手工反序列化（不依赖 Content-Type，P0 教训） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Spring 装配入口：使用默认超时配置的 RestTemplate。
     * 保留无参可注入语义，便于测试替换（MockRestServiceServer 需要受控 RestTemplate）。
     */
    public ContentSecurityServiceImpl() {
        this(defaultRestTemplate());
    }

    /** 测试可注入构造：允许传入 MockRestServiceServer 绑定的 RestTemplate；生产装配走无参构造。 */
    public ContentSecurityServiceImpl(RestTemplate restTemplate) {
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

    // ==================== stable_token 缓存 ====================

    /** 缓存令牌（volatile 保证多线程可见；竞争时 synchronized 块内仅首个线程发起 HTTP） */
    private volatile CachedToken cachedToken;

    /** stable_token 缓存载体：token + 过期时刻（毫秒 epoch） */
    private record CachedToken(String token, long expireAtMillis) {
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(appid) && StringUtils.hasText(secret);
    }

    @Override
    public String getStableAccessToken() {
        if (!isConfigured()) {
            throw new BusinessException(400, "微信内容安全检测未配置（WECHAT_APPID / WECHAT_SECRET 缺失）");
        }
        CachedToken cached = cachedToken;
        long now = System.currentTimeMillis();
        if (cached != null && now < cached.expireAtMillis()) {
            return cached.token();
        }
        synchronized (this) {
            // 双重检查：等待锁期间可能已被其他线程刷新
            cached = cachedToken;
            if (cached != null && System.currentTimeMillis() < cached.expireAtMillis()) {
                return cached.token();
            }
            return requestStableToken();
        }
    }

    /**
     * 请求 stable_token 并写入缓存（须在 synchronized 块内调用）。
     * expires_in（秒）按「过期前 5 分钟」折算缓存有效期。
     */
    private String requestStableToken() {
        Map<String, Object> reqBody = Map.of(
                "grant_type", "client_credential",
                "appid", appid,
                "secret", secret);

        String body = postJson(STABLE_TOKEN_URL, reqBody);
        Map<String, Object> resp = parseJson(body, "stable_token");

        Integer errcode = asInt(resp.get("errcode"));
        if (errcode != null && errcode != 0) {
            log.error("stable_token 获取失败 errcode={} errmsg={}", errcode, resp.get("errmsg"));
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        }
        String token = (String) resp.get("access_token");
        if (!StringUtils.hasText(token)) {
            log.error("stable_token 响应缺少 access_token");
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        }
        Integer expiresIn = asInt(resp.get("expires_in"));
        long ttl = (expiresIn == null || expiresIn <= 0 ? 7200 : expiresIn) * 1000L - TOKEN_EXPIRE_MARGIN_MS;
        cachedToken = new CachedToken(token, System.currentTimeMillis() + Math.max(ttl, 60_000L));
        log.info("stable_token 已刷新，缓存 TTL={}ms", Math.max(ttl, 60_000L));
        return token;
    }

    // ==================== 文本检测 ====================

    @Override
    public SecSuggest checkText(String openid, String content, int scene) {
        SecSuggest suggest = detectText(openid, content, scene);
        if (suggest == SecSuggest.RISKY) {
            // risky 统一拦截：文案集中维护，调用方无需重复判断
            throw new BusinessException(400, "内容包含违规信息，请修改后重试");
        }
        return suggest;
    }

    @Override
    public SecSuggest detectText(String openid, String content, int scene) {
        // 边界 1：微信凭据未配置（本地开发/测试环境）→ 跳过机审放行（生产必须配置，部署检查项）
        if (!isConfigured()) {
            log.debug("微信内容安全检测未配置，跳过文本机审（scene={}）", scene);
            return SecSuggest.PASS;
        }
        // 边界 2：历史学号账号无 openid，msgSecCheck v2 无法调用 → 跳过机审放行（产品登记边界）
        if (!StringUtils.hasText(openid)) {
            log.debug("当前用户无 openid（历史学号账号），跳过文本机审（scene={}）", scene);
            return SecSuggest.PASS;
        }
        if (!StringUtils.hasText(content)) {
            // 空文本无可检内容，直接放行（纯图 UGC 场景）
            return SecSuggest.PASS;
        }
        if (content.length() > MAX_TEXT_LENGTH) {
            throw new BusinessException(400, "内容不能超过" + MAX_TEXT_LENGTH + "字");
        }

        Map<String, Object> reqBody = Map.of(
                "version", 2,
                "scene", scene,
                "openid", openid,
                "content", content);

        String body = postJson(MSG_SEC_CHECK_URL + "?access_token=" + getStableAccessToken(), reqBody);
        Map<String, Object> resp = parseJson(body, "msg_sec_check");

        Integer errcode = asInt(resp.get("errcode"));
        if (errcode != null && errcode != 0) {
            // 40001/42001 token 失效等：fail-closed 交由用户重试（下次请求会刷新 token 缓存）
            log.error("msgSecCheck 调用失败 errcode={} errmsg={}", errcode, resp.get("errmsg"));
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        }

        // 红线：以 result.suggest 三态判定，不只看 errcode
        Object result = resp.get("result");
        String suggest = null;
        if (result instanceof Map<?, ?> resultMap) {
            Object raw = resultMap.get("suggest");
            suggest = raw == null ? null : String.valueOf(raw);
        }
        SecSuggest secSuggest = SecSuggest.fromValue(suggest);
        log.info("msgSecCheck 完成 scene={} suggest={} label={}", scene, suggest,
                result instanceof Map<?, ?> resultMap ? resultMap.get("label") : null);
        return secSuggest;
    }

    // ==================== 图片检测 ====================

    @Override
    public void checkImage(byte[] image) {
        if (image == null || image.length == 0) {
            throw new BusinessException(400, "图片内容为空");
        }
        // 大小兜底（imgSecCheck 硬限制 1MB）；720×1334 尺寸限制由前端压缩保证，后端不做像素级校验
        if (image.length > MAX_IMAGE_BYTES) {
            throw new BusinessException(400, "图片超过 1MB 限制，请压缩后重试");
        }
        if (!isConfigured()) {
            log.debug("微信内容安全检测未配置，跳过图片机审");
            return;
        }

        // imgSecCheck 为 multipart/form-data（media 字段承载图片二进制）
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("media", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return "image.jpg";
            }
        });

        String respBody;
        try {
            respBody = restTemplate.postForObject(
                    IMG_SEC_CHECK_URL + "?access_token=" + getStableAccessToken(),
                    new HttpEntity<>(body, headers), String.class);
        } catch (BusinessException e) {
            // token 获取失败等业务异常原样传播，不在此处二次包装
            throw e;
        } catch (ResourceAccessException e) {
            log.error("imgSecCheck 上游不可达", e);
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        } catch (Exception e) {
            log.error("imgSecCheck 调用失败", e);
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        }
        Map<String, Object> resp = parseJson(respBody, "img_sec_check");

        Integer errcode = asInt(resp.get("errcode"));
        if (errcode == null || errcode == 0) {
            return;
        }
        if (errcode == 87014) {
            // 违规图片统一拦截
            throw new BusinessException(400, "图片包含违规内容，无法上传");
        }
        // 其余 errcode（token 失效/媒体格式不支持等）：fail-closed，交由用户重试或换图
        log.error("imgSecCheck 调用失败 errcode={} errmsg={}", errcode, resp.get("errmsg"));
        throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
    }

    // ==================== HTTP / 解析工具 ====================

    /** POST JSON（先按 String 读取再解析，微信响应 Content-Type 为 text/plain） */
    private String postJson(String url, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return restTemplate.postForObject(url,
                    new HttpEntity<>(OBJECT_MAPPER.writeValueAsString(body), headers), String.class);
        } catch (ResourceAccessException e) {
            log.error("调用微信内容安全接口不可达（url={}）", maskUrl(url), e);
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信内容安全接口失败（url={}）", maskUrl(url), e);
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        }
    }

    /** 反序列化微信响应体（非 JSON 视为网关异常，fail-closed 500） */
    private Map<String, Object> parseJson(String body, String api) {
        if (body == null || body.isBlank()) {
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        }
        try {
            Map<String, Object> map = OBJECT_MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {
            });
            if (map == null) {
                throw new IllegalStateException("响应为空对象");
            }
            return map;
        } catch (Exception e) {
            log.error("{} 响应非 JSON：{}", api, body.length() > 200 ? body.substring(0, 200) : body);
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        }
    }

    /** errcode 双态安全解析（数字/字符串，参照 WechatService.parseErrcode） */
    private Integer asInt(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 日志脱敏：隐藏 access_token 等查询参数 */
    private String maskUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("(access_token=)[^&]*", "$1***");
    }
}
