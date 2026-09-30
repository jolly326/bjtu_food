package com.bjtufood.wechat.service.impl;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.wechat.config.WechatProperties;
import com.bjtufood.wechat.constant.WechatApiConst;
import com.bjtufood.wechat.service.WechatAccessTokenProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 微信 access_token 获取与缓存实现（stable_token）。
 * <p>
 * 架构收口 P0-B：本类自 {@code content.security.impl.ContentSecurityServiceImpl}
 * <b>原样抽取</b> stable_token 相关职责（appid/secret 读取、拉取、进程内缓存、失效清理）。
 * 抽取时<b>刻意保持逐字一致</b>的细节（这些是踩过坑的语义，改动即行为变更）：
 * <ul>
 *   <li>errcode 必须判（{@code errcode != 0} 即 fail-closed 500），不可只看 {@code access_token} 是否存在；</li>
 *   <li>{@code expires_in} 缺失 / ≤0 时按 <b>7200s</b> 兜底（微信文档上限）；</li>
 *   <li>缓存 TTL = {@code expires_in} 折算后 <b>再减 5 分钟余量</b>，并以 <b>60s 为下限</b>，
 *       避免极端情况下缓存瞬时失效造成请求风暴；</li>
 *   <li>缓存载体存<b>绝对过期时刻</b>（{@code expireAtMillis}）而非相对 TTL，配合 volatile + 双重检查，
 *       保证并发下仅首个线程真正发起 HTTP；</li>
 *   <li>失效清理 {@code invalidate()} 在 {@code synchronized} 内置空（与原实现一致）。</li>
 * </ul>
 * 超时口径：与 {@code WechatService}、{@code moderation} 一致取 5s，防止微信接口挂起拖垮调用方主链路。
 * <p>
 * <b>待跟进（不在本次收口范围）</b>：未配置凭据与拉取失败时的提示文案沿用原「内容安全检测服务…」措辞，
 * 以保持 moderation 链路对终端用户返回的文案不变；但本类同时服务 {@code upload}
 * （云存储 batchdownloadfile），该措辞在上传失败场景下不够贴切，可后续统一为「微信服务…」口径。
 */
@Slf4j
@Service
public class WechatAccessTokenProviderImpl implements WechatAccessTokenProvider {

    /** 连接/读取超时（毫秒）：与 WechatService 一致，防止微信接口挂起拖垮 UGC 主链路 */
    private static final int WECHAT_TIMEOUT_MS = 5000;

    /** stable_token 缓存提前失效余量：过期前 5 分钟即视为失效（红线要求 ≥5 分钟） */
    private static final long TOKEN_EXPIRE_MARGIN_MS = 5 * 60 * 1000L;

    /** {@code expires_in} 缺失 / 非法时的兜底 TTL（秒）——微信文档给出的 stable_token 上限 */
    private static final int DEFAULT_TOKEN_TTL_SECONDS = 7200;

    /** 缓存 TTL 下限（毫秒）：防止折算结果过小导致缓存瞬时失效、请求风暴 */
    private static final long MIN_CACHE_TTL_MS = 60_000L;

    /** 微信响应固定以 text/plain 返回，统一先取 String 再手工反序列化（不依赖 Content-Type，P0 教训） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;

    /**
     * 微信开放平台配置（类型化绑定，单一真源）。
     * <p>
     * 架构收口 P2：本类原以 {@code @Value} 自行绑定 {@code wechat.appid} /
     * {@code wechat.secret}，与 {@code WechatService} 重复绑定同一份凭据，且「是否已配置」
     * 在两处各写一份判空逻辑。现统一由 {@link WechatProperties} 承载，
     * {@link #isConfigured()} 亦改为委托，杜绝判据分裂。
     */
    private final WechatProperties wechatProperties;

    // ==================== stable_token 缓存 ====================

    /** 缓存令牌（volatile 保证多线程可见；竞争时 synchronized 块内仅首个线程发起 HTTP） */
    private volatile CachedToken cachedToken;

    /** stable_token 缓存载体：token + 过期时刻（毫秒 epoch） */
    private record CachedToken(String token, long expireAtMillis) {
    }

    /**
     * Spring 装配入口：使用默认超时配置的 RestTemplate。
     * <p>
     * <b>必须显式 {@code @Autowired}</b>：本类有两个构造器（生产装配 / 测试注入），
     * Spring 在多构造器且无默认构造器时无法自动选择，会导致上下文加载失败。
     */
    @Autowired
    public WechatAccessTokenProviderImpl(WechatProperties wechatProperties) {
        this(defaultRestTemplate(), wechatProperties);
    }

    /**
     * 测试可注入构造：允许传入 MockRestServiceServer 绑定的 RestTemplate 与受控配置对象；
     * 生产装配走 {@link #WechatAccessTokenProviderImpl(WechatProperties)}。
     */
    public WechatAccessTokenProviderImpl(RestTemplate restTemplate, WechatProperties wechatProperties) {
        this.restTemplate = restTemplate;
        this.wechatProperties = wechatProperties;
    }

    private static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(WECHAT_TIMEOUT_MS);
        factory.setReadTimeout(WECHAT_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    @Override
    public boolean isConfigured() {
        // 委托 WechatProperties——与 WechatService 共用同一判据，杜绝两处各判一次导致的口径分裂
        return wechatProperties.isConfigured();
    }

    @Override
    public void invalidate() {
        // 清空 access_token 缓存（BE-06 自愈入口）。
        // 仅在识别到 40001（invalid credential）/ 42001（access_token expired）时调用：
        // 旧实现缓存不失效，导致 token 提前失效后全量 UGC 被 fail-closed 阻断最长 2 小时（缓存 TTL）。
        synchronized (this) {
            cachedToken = null;
        }
    }

    @Override
    public String get() {
        if (!isConfigured()) {
            throw new BusinessException(400, "微信服务未配置（WECHAT_APPID / WECHAT_SECRET 缺失）");
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
                "appid", wechatProperties.getAppid(),
                "secret", wechatProperties.getSecret());

        String body = postJson(WechatApiConst.STABLE_TOKEN_URL, reqBody);
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
        long ttl = (expiresIn == null || expiresIn <= 0 ? DEFAULT_TOKEN_TTL_SECONDS : expiresIn)
                * 1000L - TOKEN_EXPIRE_MARGIN_MS;
        cachedToken = new CachedToken(token, System.currentTimeMillis() + Math.max(ttl, MIN_CACHE_TTL_MS));
        log.info("stable_token 已刷新，缓存 TTL={}ms", Math.max(ttl, MIN_CACHE_TTL_MS));
        return token;
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
            log.error("调用微信接口不可达（url={}）", maskUrl(url), e);
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信接口失败（url={}）", maskUrl(url), e);
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
